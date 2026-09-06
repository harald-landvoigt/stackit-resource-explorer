package com.landvoigtit.stackit.resourceexplorer.network;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NetworkResourceDto {

    @NotBlank
    private String loadBalancerId;

    @NotBlank
    private String name;

    private String ipAddress;
    private String region;
    private java.util.Map<String, String> labels;
}
