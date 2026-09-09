package com.landvoigtit.stackit.resourceexplorer.storage;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class StorageResourceDto {

    @NotBlank
    private String bucketName;

    @NotBlank
    private String region;

    private String storageClass;

    private Boolean objectLockEnabled;

    private String urlPathStyle;

    private String urlVirtualHostedStyle;

    private Boolean isPublic;

    private String publicAccessType;

    private String bucketPolicy;

    private StorageAclDto acl;

    private StorageRetentionDto retention;

    private List<String> securityFindings;

    @Getter
    @Setter
    public static class StorageAclDto {
        private String owner;
        private String ownerId;
        private List<StorageGrantDto> grants;
    }

    @Getter
    @Setter
    public static class StorageGrantDto {
        private String grantee;
        private String granteeType;
        private String permission;
    }

    @Getter
    @Setter
    public static class StorageRetentionDto {
        private String mode;
        private Integer retentionDays;
        private Boolean defaultRetentionSet;
        private Integer projectMaxRetentionDays;
    }
}
