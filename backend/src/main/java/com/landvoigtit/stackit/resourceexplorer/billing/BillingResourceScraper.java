package com.landvoigtit.stackit.resourceexplorer.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.landvoigtit.stackit.resourceexplorer.StackitProjectDiscoveryService;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class BillingResourceScraper {

    @Inject
    StackitProjectDiscoveryService projectDiscoveryService;

    @Inject
    BillingApiClient billingApiClient;

    @Inject
    StackitResourceRepository repository;

    @Inject
    Validator validator;

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Scheduled(every = "${stackit.billing.schedule:12h}")
    public void scrape() {
        log.info("Starting Billing/Cost resource scrape...");
        try {
            final String orgId = projectDiscoveryService.discoverOrganizationId();
            if (orgId == null || orgId.isBlank()) {
                log.warn("Cannot scrape costs: Organization ID could not be discovered.");
                return;
            }

            final LocalDate now = LocalDate.now(ZoneOffset.UTC);
            final String from = now.withDayOfMonth(1).toString();
            final String to = now.with(TemporalAdjusters.lastDayOfMonth()).toString();

            log.info("Starting Cost API scrape for organization {} (period: {} to {})...", orgId, from, to);
            final List<String> currentResourceIds = new ArrayList<>();
            final boolean success = scrapeOrganizationCosts(orgId, from, to, currentResourceIds);

            if (success) {
                log.info("Cost API resource scrape completed successfully. Processed {} cost records.", currentResourceIds.size());
            }
        } catch (final Exception e) {
            log.error("Failed to scrape Billing/Cost resources", e);
        }
    }

    private boolean scrapeOrganizationCosts(
            final String orgId,
            final String from,
            final String to,
            final List<String> currentResourceIds
    ) {
        try {
            final String costsJson = billingApiClient.getProjectCosts(orgId, from, to);
            if (costsJson == null || costsJson.trim().isEmpty()) {
                log.warn("Empty response received from Cost API for organization {}", orgId);
                return false;
            }

            final JsonNode rootNode = objectMapper.readTree(costsJson);
            if (!rootNode.isArray()) {
                log.warn("Unexpected Cost API response format for organization {}: expected array but got {}", orgId, rootNode.getNodeType());
                return false;
            }

            double totalOrgChargeCents = 0.0;
            final List<String> projectCostResourceIds = new ArrayList<>();

            for (final JsonNode node : rootNode) {
                final CostProjectDto dto = objectMapper.treeToValue(node, CostProjectDto.class);
                if (dto == null || dto.getProjectId() == null) {
                    continue;
                }

                if (validator.validate(dto).isEmpty()) {
                    final StackitEntity entity = BillingMapper.mapCostToEntity(dto, from, to);
                    repository.persistOrUpdate(entity);
                    currentResourceIds.add(entity.getResourceId());
                    projectCostResourceIds.add(entity.getResourceId());

                    if (dto.getTotalCharge() != null) {
                        totalOrgChargeCents += dto.getTotalCharge();
                    }

                    // Soft-delete older costs for this specific project if not matching current resource ID
                    repository.softDeleteMissing(StackitConstants.RESOURCE_TYPE_BILLING, dto.getProjectId(), List.of(entity.getResourceId()));
                } else {
                    log.warn("Invalid Cost DTO for project: {}", dto.getProjectId());
                }
            }

            // Persist organization-level aggregate summary entity
            final String orgResourceId = String.format("cost-org-%s-%s", orgId, from);
            final StackitEntity orgEntity = new StackitEntity();
            final UUID orgUuid = UUID.nameUUIDFromBytes(orgResourceId.getBytes());
            orgEntity.setId(orgUuid);
            orgEntity.setResourceId(orgResourceId);
            orgEntity.setName("Organization");
            orgEntity.setType(StackitConstants.RESOURCE_TYPE_BILLING_ORG);
            orgEntity.setStatus(StackitConstants.STATUS_ACTIVE);
            orgEntity.setRegion(StackitConstants.GLOBAL_REGION);
            orgEntity.setProjectId(orgId);
            orgEntity.setCreatedAt(Instant.now());
            orgEntity.setUpdatedAt(Instant.now());
            orgEntity.setData(Map.of(
                    "amount", totalOrgChargeCents / 100.0,
                    "currency", "EUR",
                    "totalChargeCents", totalOrgChargeCents,
                    "customerAccountId", orgId,
                    "from", from,
                    "to", to
            ));
            repository.persistOrUpdate(orgEntity);
            currentResourceIds.add(orgResourceId);

            repository.softDeleteMissing(StackitConstants.RESOURCE_TYPE_BILLING_ORG, orgId, List.of(orgResourceId));

            return true;
        } catch (final Exception e) {
            log.warn("Failed to fetch project costs for organization {}: {}", orgId, e.getMessage());
            return false;
        }
    }
}
