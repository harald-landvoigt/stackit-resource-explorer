package com.landvoigtit.stackit.resourceexplorer;

import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class StackitResourceService {

    private final StackitResourceRepository repository;
    private final Validator validator;
    private final StackitProjectDiscoveryService projectDiscoveryService;
    private final com.landvoigtit.stackit.resourceexplorer.billing.BillingResourceScraper billingScraper;

    @Inject
    public StackitResourceService(
            final StackitResourceRepository repository,
            final Validator validator,
            final StackitProjectDiscoveryService projectDiscoveryService,
            final com.landvoigtit.stackit.resourceexplorer.billing.BillingResourceScraper billingScraper) {
        this.repository = repository;
        this.validator = validator;
        this.projectDiscoveryService = projectDiscoveryService;
        this.billingScraper = billingScraper;
    }

    public final StackitEntity mapToEntity(final StackitResourceDto dto) {
        if (dto == null) {
            return null;
        }
        final StackitEntity entity = new StackitEntity();
        if (dto.getId() != null) {
            entity.setId(UUID.fromString(dto.getId()));
        }
        entity.setResourceId(dto.getResourceId());
        entity.setName(dto.getName());
        entity.setType(dto.getType());
        entity.setStatus(dto.getStatus());
        entity.setRegion(dto.getRegion());
        entity.setProjectId(dto.getProjectId());
        if (dto.getCreatedAt() != null) {
            entity.setCreatedAt(dto.getCreatedAt().toInstant());
        }
        if (dto.getUpdatedAt() != null) {
            entity.setUpdatedAt(dto.getUpdatedAt().toInstant());
        }
        if (dto.getDeletedAt() != null) {
            entity.setDeletedAt(dto.getDeletedAt().toInstant());
        }
        entity.setTags(dto.getTags());
        entity.setData(dto.getData());
        return entity;
    }

    public final StackitResourceDto mapToDto(final StackitEntity entity) {
        if (entity == null) {
            return null;
        }
        final StackitResourceDto dto = new StackitResourceDto();
        if (entity.getId() != null) {
            dto.setId(entity.getId().toString());
        }
        dto.setResourceId(entity.getResourceId());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setStatus(entity.getStatus());
        dto.setRegion(entity.getRegion());
        dto.setProjectId(entity.getProjectId());
        if (entity.getCreatedAt() != null) {
            dto.setCreatedAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        }
        if (entity.getUpdatedAt() != null) {
            dto.setUpdatedAt(entity.getUpdatedAt().atOffset(ZoneOffset.UTC));
        }
        if (entity.getDeletedAt() != null) {
            dto.setDeletedAt(entity.getDeletedAt().atOffset(ZoneOffset.UTC));
        }
        dto.setTags(entity.getTags());
        dto.setData(entity.getData());
        return dto;
    }

    public final void validate(final StackitResourceDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("DTO cannot be null");
        }
        final Set<ConstraintViolation<StackitResourceDto>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            final String message = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(message);
        }
    }

    @Transactional
    public final StackitResourceDto save(final StackitResourceDto dto) {
        validate(dto);
        final StackitEntity entity = mapToEntity(dto);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }
        entity.setUpdatedAt(Instant.now());
        repository.persist(entity);
        return mapToDto(entity);
    }

    public final StackitResourceDto findById(final String id) {
        try {
            final UUID uuid = UUID.fromString(id);
            final StackitEntity entity = repository.findById(uuid);
            return mapToDto(entity);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    public final ResourceSearchResultDto searchResources(final String query) {
        final List<StackitEntity> list = repository.search(query, 100);
        final List<StackitResourceDto> resourceDtos = list.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        final List<AggregationItemDto> rawTypeAggs = repository.aggregateByType(query);
        final java.util.Map<String, Long> consolidatedTypes = new java.util.LinkedHashMap<>();
        for (final AggregationItemDto agg : rawTypeAggs) {
            final String formattedLabel = formatTypeLabel(agg.getKey());
            consolidatedTypes.put(formattedLabel, consolidatedTypes.getOrDefault(formattedLabel, 0L) + agg.getCount());
        }
        final List<AggregationItemDto> formattedTypeAggs = consolidatedTypes.entrySet().stream()
                .map(e -> new AggregationItemDto(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        final List<AggregationItemDto> regionAggs = repository.aggregateByRegion(query);
        final List<AggregationItemDto> statusAggs = repository.aggregateByStatus(query);

        final long totalCount = formattedTypeAggs.stream().mapToLong(AggregationItemDto::getCount).sum();
        return new ResourceSearchResultDto(resourceDtos, totalCount, formattedTypeAggs, regionAggs, statusAggs);
    }

    public final String formatTypeLabel(final String type) {
        if (type == null || type.isBlank()) {
            return "Unknown";
        }
        switch (type.toLowerCase()) {
            case "compute":
            case "vm":
                return "VMs";
            case "storage":
                return "Buckets";
            case "vmdisks":
            case "disk":
            case "volume":
                return "VM Disks";
            case "network-vpc":
                return "VPCs";
            case "network":
                return "Load Balancers";
            case "billing":
            case "billing-org":
                return "Invoices";
            case "iam":
                return "IAM Policies";
            default:
                return Character.toUpperCase(type.charAt(0)) + type.substring(1) + "s";
        }
    }

    public final List<StackitResourceDto> listAll() {
        return listAll(null);
    }

    public final List<StackitResourceDto> listAll(final String query) {
        return searchResources(query).getResources();
    }

    public final List<BillingSummaryDto> getBillingSummary() {
        // Get current year and month in UTC
        final ZonedDateTime nowUtc = Instant.now().atZone(ZoneOffset.UTC);
        final int currentYear = nowUtc.getYear();
        final int currentMonth = nowUtc.getMonthValue();

        // Fetch all active billing/billing-org resources
        List<StackitEntity> billingEntities = repository != null
                ? repository.list("type in (?1, ?2) and deletedAt is null",
                        StackitConstants.RESOURCE_TYPE_BILLING,
                        StackitConstants.RESOURCE_TYPE_BILLING_ORG)
                : java.util.Collections.emptyList();

        // If no billing data exists in repository yet, trigger an on-demand scrape
        if (billingEntities.isEmpty() && billingScraper != null && repository != null) {
            billingScraper.scrape();
            billingEntities = repository.list("type in (?1, ?2) and deletedAt is null",
                    StackitConstants.RESOURCE_TYPE_BILLING,
                    StackitConstants.RESOURCE_TYPE_BILLING_ORG);
        }

        // Filter for current month in UTC
        final List<StackitEntity> currentMonthEntities = billingEntities.stream()
                .filter(e -> {
                    if (e.getCreatedAt() == null) {
                        return false;
                    }
                    final ZonedDateTime zdt = e.getCreatedAt().atZone(ZoneOffset.UTC);
                    return zdt.getYear() == currentYear && zdt.getMonthValue() == currentMonth;
                })
                .collect(Collectors.toList());

        // Discover projects to map names
        final List<cloud.stackit.sdk.resourcemanager.v0api.model.Project> projects = projectDiscoveryService != null 
                ? projectDiscoveryService.discoverProjects() 
                : java.util.Collections.emptyList();

        final java.util.Map<String, String> projectNames = new java.util.HashMap<>();
        if (projects != null) {
            for (final cloud.stackit.sdk.resourcemanager.v0api.model.Project p : projects) {
                if (p.getProjectId() != null && p.getName() != null) {
                    projectNames.put(p.getProjectId().toString(), p.getName());
                }
            }
        }

        // Group and aggregate by projectId, type, and currency
        final java.util.Map<String, BillingSummaryDto> aggregated = new java.util.LinkedHashMap<>();

        for (final StackitEntity entity : currentMonthEntities) {
            final String projectId = entity.getProjectId() != null ? entity.getProjectId() : StackitConstants.UNKNOWN_PROJECT_ID;
            final String type = StackitConstants.RESOURCE_TYPE_BILLING_ORG.equalsIgnoreCase(entity.getType()) ? "Organization" : "Project";
            
            Double amount = 0.0;
            String currency = "";
            if (entity.getData() != null) {
                final Object amt = entity.getData().get("amount");
                if (amt instanceof Number) {
                    amount = ((Number) amt).doubleValue();
                }
                final Object curr = entity.getData().get("currency");
                if (curr != null) {
                    currency = curr.toString();
                }
            }

            final String aggKey = projectId + ":" + type + ":" + currency;
            if (aggregated.containsKey(aggKey)) {
                final BillingSummaryDto existing = aggregated.get(aggKey);
                existing.setAmount(existing.getAmount() + amount);
            } else {
                String name = projectId;
                if ("Organization".equals(type)) {
                    name = "Organization";
                } else if (projectNames.containsKey(projectId)) {
                    name = projectNames.get(projectId);
                } else if (entity.getName() != null && !entity.getName().isBlank()) {
                    name = entity.getName();
                }
                
                final BillingSummaryDto summary = new BillingSummaryDto(
                        projectId,
                        name,
                        type,
                        amount,
                        currency
                );
                aggregated.put(aggKey, summary);
            }
        }

        final List<BillingSummaryDto> result = new java.util.ArrayList<>(aggregated.values());
        result.sort((final BillingSummaryDto a, final BillingSummaryDto b) -> {
            final boolean aIsOrg = "Organization".equalsIgnoreCase(a.getType());
            final boolean bIsOrg = "Organization".equalsIgnoreCase(b.getType());
            if (aIsOrg && !bIsOrg) {
                return -1;
            }
            if (!aIsOrg && bIsOrg) {
                return 1;
            }
            final double aAmount = a.getAmount() != null ? a.getAmount() : 0.0;
            final double bAmount = b.getAmount() != null ? b.getAmount() : 0.0;
            final int cmp = Double.compare(bAmount, aAmount); // descending by cost
            if (cmp != 0) {
                return cmp;
            }
            final String aName = a.getName() != null ? a.getName() : "";
            final String bName = b.getName() != null ? b.getName() : "";
            return aName.compareToIgnoreCase(bName);
        });

        return result;
    }
}
