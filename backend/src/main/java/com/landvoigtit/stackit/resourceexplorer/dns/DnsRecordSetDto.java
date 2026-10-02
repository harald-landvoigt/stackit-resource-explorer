package com.landvoigtit.stackit.resourceexplorer.dns;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DnsRecordSetDto {

    private String id;
    private String name;
    private String type;
    private Integer ttl;
    private List<String> records;
    private String comment;
    private Boolean active;
    private String state;
    private String matchedPublicIpId;
    private String matchedServerId;
    private String matchedServerName;
    private String zoneId;
    private String zoneName;
}
