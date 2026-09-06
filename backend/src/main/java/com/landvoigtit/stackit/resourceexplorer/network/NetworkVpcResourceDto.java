package com.landvoigtit.stackit.resourceexplorer.network;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NetworkVpcResourceDto {

    @NotBlank
    private String networkId;

    @NotBlank
    private String name;

    @NotBlank
    private String status;

    private List<String> prefixes;
    private String gateway;
    private String publicIp;
    private Boolean routed;
    private List<String> nameservers;
    private java.util.Map<String, String> labels;
}
