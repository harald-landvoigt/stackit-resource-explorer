package com.landvoigtit.stackit.resourceexplorer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AggregationItemDto {
    private String key;
    private long count;

    public String getType() {
        return key;
    }
}
