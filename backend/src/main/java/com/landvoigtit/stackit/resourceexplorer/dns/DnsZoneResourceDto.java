package com.landvoigtit.stackit.resourceexplorer.dns;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DnsZoneResourceDto {

    @NotBlank
    private String zoneId;

    @NotBlank
    private String name;

    @NotBlank
    private String dnsName;

    @NotBlank
    private String status;

    private String zoneType;
    private String visibility;
    private Boolean isReverseZone;
    private Boolean active;
    private String primaryNameServer;
    private String contactEmail;
    private Integer defaultTTL;
    private Integer recordCount;
    private String acl;
    private String description;
    private Map<String, String> labels;
    private Map<String, Integer> recordSetsSummary;
    private List<DnsRecordSetDto> recordSets;
}
