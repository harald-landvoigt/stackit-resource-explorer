package com.landvoigtit.stackit.resourceexplorer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AggregationItemDto {
    private String key;
    private long count;
    private String rawKey;

    public AggregationItemDto(final String key, final long count) {
        this.key = key;
        this.count = count;
        this.rawKey = key;
    }

    public AggregationItemDto(final String key, final long count, final String rawKey) {
        this.key = key;
        this.count = count;
        this.rawKey = rawKey;
    }

    public String getType() {
        return key;
    }
}
