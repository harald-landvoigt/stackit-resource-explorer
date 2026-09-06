package com.landvoigtit.stackit.resourceexplorer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResourceSearchResultDto {
    private List<StackitResourceDto> resources;
    private long totalCount;
    private List<AggregationItemDto> typeAggregations;
    private List<AggregationItemDto> regionAggregations;
    private List<AggregationItemDto> statusAggregations;

    // Backward compatibility alias for typeAggregations
    public List<AggregationItemDto> getAggregations() {
        return typeAggregations;
    }
}
