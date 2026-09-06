package com.landvoigtit.stackit.resourceexplorer.storage;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StorageResourceDto {

    @NotBlank
    private String bucketName;

    @NotBlank
    private String region;

    private String storageClass;
}
