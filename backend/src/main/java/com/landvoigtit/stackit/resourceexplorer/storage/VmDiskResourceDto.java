package com.landvoigtit.stackit.resourceexplorer.storage;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VmDiskResourceDto {

    @NotBlank
    private String volumeId;

    @NotBlank
    private String name;

    @NotBlank
    private String status;

    private Long sizeGb;
    private String performanceClass;
    private String availabilityZone;
    private Boolean bootable;
    private Boolean encrypted;
    private String serverId;
    private java.util.Map<String, String> labels;
}
