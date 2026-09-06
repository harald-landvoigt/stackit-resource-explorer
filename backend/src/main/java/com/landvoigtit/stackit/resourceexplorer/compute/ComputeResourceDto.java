package com.landvoigtit.stackit.resourceexplorer.compute;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ComputeResourceDto {

    @NotBlank
    private String serverId;

    @NotBlank
    private String name;

    @NotBlank
    private String status;

    private String powerStatus;
    private String machineType;
    private String availabilityZone;
    private String imageId;
    private String bootVolumeId;
    private Boolean bootVolumeDeleteOnTermination;
    private List<String> attachedVolumes;
    private String keypairName;
    private List<String> securityGroups;
    private List<String> ipAddresses;
    private Map<String, String> labels;
    private OffsetDateTime launchedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Backward compatibility helpers
    public String getSize() {
        return machineType;
    }

    public void setSize(final String size) {
        this.machineType = size;
    }

    public String getImage() {
        return imageId;
    }

    public void setImage(final String image) {
        this.imageId = image;
    }
}
