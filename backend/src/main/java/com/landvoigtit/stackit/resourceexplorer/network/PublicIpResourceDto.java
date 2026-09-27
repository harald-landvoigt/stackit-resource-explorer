package com.landvoigtit.stackit.resourceexplorer.network;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublicIpResourceDto {

    @NotBlank
    private String publicIpId;

    @NotBlank
    private String ip;

    @NotBlank
    private String name;

    @NotBlank
    private String status;

    private String region;
    private Boolean attached;
    private String networkInterfaceId;
    private String serverId;
    private String serverName;
    private Map<String, String> labels;
}
