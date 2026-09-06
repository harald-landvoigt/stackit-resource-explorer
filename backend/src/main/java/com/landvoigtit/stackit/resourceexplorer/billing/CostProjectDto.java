package com.landvoigtit.stackit.resourceexplorer.billing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CostProjectDto {

    private String customerAccountId;

    @NotBlank
    private String projectId;

    private String projectName;

    @NotNull
    private Double totalCharge; // In cents

    private Double totalDiscount; // In cents
}
