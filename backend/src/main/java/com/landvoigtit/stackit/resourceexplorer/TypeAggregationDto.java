package com.landvoigtit.stackit.resourceexplorer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TypeAggregationDto {
    private String type;
    private long count;
}
