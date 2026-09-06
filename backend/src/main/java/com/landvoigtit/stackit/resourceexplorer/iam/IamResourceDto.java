package com.landvoigtit.stackit.resourceexplorer.iam;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IamResourceDto {

    @NotBlank
    private String memberId;

    @NotBlank
    private String role;
}
