package com.landvoigtit.stackit.resourceexplorer;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BillingSummaryDto {
    private String id;
    private String name;
    private String type; // "Project" or "Organization"
    private Double amount;
    private String currency;
}
