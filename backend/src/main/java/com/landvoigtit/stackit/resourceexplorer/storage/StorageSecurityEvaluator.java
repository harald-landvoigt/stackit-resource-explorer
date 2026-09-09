package com.landvoigtit.stackit.resourceexplorer.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class StorageSecurityEvaluator {

    public static final String ALL_USERS_URI = "http://acs.amazonaws.com/groups/global/AllUsers";
    public static final String AUTH_USERS_URI = "http://acs.amazonaws.com/groups/global/AuthenticatedUsers";

    public static final String FINDING_PUBLIC_READ_VIA_ACL = "PUBLIC_READ_VIA_ACL";
    public static final String FINDING_PUBLIC_WRITE_VIA_ACL = "PUBLIC_WRITE_VIA_ACL";
    public static final String FINDING_PUBLIC_READ_VIA_POLICY = "PUBLIC_READ_VIA_POLICY";
    public static final String FINDING_PUBLIC_WRITE_VIA_POLICY = "PUBLIC_WRITE_VIA_POLICY";
    public static final String FINDING_MISSING_TLS_ENFORCEMENT = "MISSING_TLS_ENFORCEMENT";
    public static final String FINDING_NO_DEFAULT_RETENTION = "NO_DEFAULT_RETENTION";
    public static final String FINDING_PUBLIC_ACCESS_BLOCKED = "PUBLIC_ACCESS_BLOCKED_BY_CONFIGURATION";
    public static final String FINDING_ACL_NOT_ACCESSIBLE = "ACL_NOT_ACCESSIBLE";

    public static final String ACCESS_TYPE_NOT_PUBLIC = "NOT_PUBLIC";
    public static final String ACCESS_TYPE_PUBLIC_READ = "PUBLIC_READ";
    public static final String ACCESS_TYPE_PUBLIC_WRITE = "PUBLIC_WRITE";
    public static final String ACCESS_TYPE_PUBLIC_READ_WRITE = "PUBLIC_READ_WRITE";
    public static final String ACCESS_TYPE_UNKNOWN = "UNKNOWN";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public record SecurityEvaluationResult(
            Boolean isPublic,
            String publicAccessType,
            List<String> securityFindings
    ) {}

    public static void evaluateAndEnrich(final StorageResourceDto dto, final Boolean publicAccessBlockEnabled) {
        if (dto == null) {
            return;
        }
        final SecurityEvaluationResult result = evaluate(
                dto.getAcl(),
                dto.getBucketPolicy(),
                publicAccessBlockEnabled,
                dto.getRetention()
        );
        dto.setIsPublic(result.isPublic());
        dto.setPublicAccessType(result.publicAccessType());
        dto.setSecurityFindings(result.securityFindings());
    }

    public static SecurityEvaluationResult evaluate(
            final StorageResourceDto.StorageAclDto acl,
            final String bucketPolicyJson,
            final Boolean publicAccessBlockEnabled,
            final StorageResourceDto.StorageRetentionDto retention) {

        final List<String> findings = new ArrayList<>();
        boolean aclAllowsRead = false;
        boolean aclAllowsWrite = false;

        // 1. ACL Evaluation
        if (acl == null) {
            findings.add(FINDING_ACL_NOT_ACCESSIBLE);
        } else if (acl.getGrants() != null) {
            for (final StorageResourceDto.StorageGrantDto grant : acl.getGrants()) {
                final String grantee = grant.getGrantee();
                final String permission = grant.getPermission() != null ? grant.getPermission().toUpperCase() : "";

                if (isPublicGrantee(grantee)) {
                    if ("READ".equals(permission) || "FULL_CONTROL".equals(permission)) {
                        aclAllowsRead = true;
                        if (!findings.contains(FINDING_PUBLIC_READ_VIA_ACL)) {
                            findings.add(FINDING_PUBLIC_READ_VIA_ACL);
                        }
                    }
                    if ("WRITE".equals(permission) || "FULL_CONTROL".equals(permission)) {
                        aclAllowsWrite = true;
                        if (!findings.contains(FINDING_PUBLIC_WRITE_VIA_ACL)) {
                            findings.add(FINDING_PUBLIC_WRITE_VIA_ACL);
                        }
                    }
                }
            }
        }

        // 2. Bucket Policy Evaluation
        boolean policyAllowsRead = false;
        boolean policyAllowsWrite = false;
        boolean tlsEnforced = false;

        if (bucketPolicyJson != null && !bucketPolicyJson.isBlank()) {
            try {
                final JsonNode rootNode = OBJECT_MAPPER.readTree(bucketPolicyJson);
                final JsonNode statements = rootNode.get("Statement");
                if (statements != null && statements.isArray()) {
                    for (final JsonNode statement : statements) {
                        final String effect = statement.path("Effect").asText();

                        // Check TLS enforcement: Deny statement where aws:SecureTransport is false
                        if ("Deny".equalsIgnoreCase(effect) && hasInsecureTransportCondition(statement)) {
                            tlsEnforced = true;
                        }

                        // Check public exposure: Allow statement with wildcard Principal
                        if ("Allow".equalsIgnoreCase(effect) && isWildcardPrincipal(statement.get("Principal"))) {
                            final List<String> actions = extractActions(statement.get("Action"));
                            for (final String action : actions) {
                                final String lowerAction = action.toLowerCase();
                                if (lowerAction.equals("*") || lowerAction.equals("s3:*")
                                        || lowerAction.startsWith("s3:get") || lowerAction.startsWith("s3:list")) {
                                    policyAllowsRead = true;
                                    if (!findings.contains(FINDING_PUBLIC_READ_VIA_POLICY)) {
                                        findings.add(FINDING_PUBLIC_READ_VIA_POLICY);
                                    }
                                }
                                if (lowerAction.equals("*") || lowerAction.equals("s3:*")
                                        || lowerAction.startsWith("s3:put") || lowerAction.startsWith("s3:delete")) {
                                    policyAllowsWrite = true;
                                    if (!findings.contains(FINDING_PUBLIC_WRITE_VIA_POLICY)) {
                                        findings.add(FINDING_PUBLIC_WRITE_VIA_POLICY);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (final Exception e) {
                log.warn("Failed to parse bucket policy JSON: {}", e.getMessage());
            }
        }

        if (!tlsEnforced) {
            findings.add(FINDING_MISSING_TLS_ENFORCEMENT);
        }

        // 3. Retention Evaluation
        if (retention == null || !Boolean.TRUE.equals(retention.getDefaultRetentionSet())) {
            findings.add(FINDING_NO_DEFAULT_RETENTION);
        }

        // 4. Consolidated Public Status Evaluation
        final boolean publicAccessBlocked = Boolean.TRUE.equals(publicAccessBlockEnabled);
        final boolean potentiallyPublic = aclAllowsRead || aclAllowsWrite || policyAllowsRead || policyAllowsWrite;

        if (publicAccessBlocked) {
            if (potentiallyPublic) {
                findings.add(FINDING_PUBLIC_ACCESS_BLOCKED);
            }
            return new SecurityEvaluationResult(false, ACCESS_TYPE_NOT_PUBLIC, Collections.unmodifiableList(findings));
        }

        final boolean canRead = aclAllowsRead || policyAllowsRead;
        final boolean canWrite = aclAllowsWrite || policyAllowsWrite;

        if (canRead && canWrite) {
            return new SecurityEvaluationResult(true, ACCESS_TYPE_PUBLIC_READ_WRITE, Collections.unmodifiableList(findings));
        } else if (canRead) {
            return new SecurityEvaluationResult(true, ACCESS_TYPE_PUBLIC_READ, Collections.unmodifiableList(findings));
        } else if (canWrite) {
            return new SecurityEvaluationResult(true, ACCESS_TYPE_PUBLIC_WRITE, Collections.unmodifiableList(findings));
        } else if (acl == null) {
            return new SecurityEvaluationResult(null, ACCESS_TYPE_UNKNOWN, Collections.unmodifiableList(findings));
        } else {
            return new SecurityEvaluationResult(false, ACCESS_TYPE_NOT_PUBLIC, Collections.unmodifiableList(findings));
        }
    }

    private static boolean isPublicGrantee(final String grantee) {
        if (grantee == null) {
            return false;
        }
        return ALL_USERS_URI.equalsIgnoreCase(grantee) || AUTH_USERS_URI.equalsIgnoreCase(grantee);
    }

    private static boolean isWildcardPrincipal(final JsonNode principalNode) {
        if (principalNode == null) {
            return false;
        }
        if (principalNode.isTextual() && "*".equals(principalNode.asText())) {
            return true;
        }
        if (principalNode.isObject()) {
            final JsonNode awsNode = principalNode.get("AWS");
            if (awsNode != null) {
                if (awsNode.isTextual() && "*".equals(awsNode.asText())) {
                    return true;
                }
                if (awsNode.isArray()) {
                    for (final JsonNode elem : awsNode) {
                        if ("*".equals(elem.asText())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static List<String> extractActions(final JsonNode actionNode) {
        if (actionNode == null) {
            return Collections.emptyList();
        }
        final List<String> actions = new ArrayList<>();
        if (actionNode.isTextual()) {
            actions.add(actionNode.asText());
        } else if (actionNode.isArray()) {
            for (final JsonNode elem : actionNode) {
                if (elem.isTextual()) {
                    actions.add(elem.asText());
                }
            }
        }
        return actions;
    }

    private static boolean hasInsecureTransportCondition(final JsonNode statement) {
        final JsonNode condition = statement.get("Condition");
        if (condition == null || !condition.isObject()) {
            return false;
        }
        final JsonNode boolCondition = condition.get("Bool");
        if (boolCondition != null && boolCondition.isObject()) {
            final JsonNode secureTransport = boolCondition.get("aws:SecureTransport");
            if (secureTransport != null) {
                return "false".equalsIgnoreCase(secureTransport.asText());
            }
        }
        return false;
    }
}
