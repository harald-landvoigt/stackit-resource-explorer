package com.landvoigtit.stackit.resourceexplorer.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class BillingMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public static BillingInvoiceDto mapToDto(final String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawJson, BillingInvoiceDto.class);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Failed to parse invoice JSON", e);
        }
    }

    public static StackitEntity mapToEntity(final BillingInvoiceDto dto) {
        if (dto == null) {
            return null;
        }
        final StackitEntity entity = new StackitEntity();
        if (dto.getId() != null) {
            final UUID uuid = UUID.nameUUIDFromBytes(dto.getId().getBytes());
            entity.setId(uuid);
            entity.setResourceId(dto.getId());
        }
        entity.setName(dto.getInvoiceNumber());
        entity.setType(StackitConstants.RESOURCE_TYPE_BILLING);
        entity.setStatus(dto.getStatus());
        entity.setRegion(StackitConstants.DEFAULT_REGION);
        entity.setProjectId(StackitConstants.UNKNOWN_PROJECT_ID);
        if (dto.getBillingDate() != null) {
            entity.setCreatedAt(dto.getBillingDate().toInstant());
        } else {
            entity.setCreatedAt(Instant.now());
        }
        entity.setUpdatedAt(Instant.now());
        entity.setData(Map.of(
            "amount", dto.getAmount() != null ? dto.getAmount() : 0.0,
            "currency", dto.getCurrency() != null ? dto.getCurrency() : ""
        ));
        return entity;
    }

    public static CostProjectDto mapCostDto(final String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawJson, CostProjectDto.class);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Failed to parse cost JSON", e);
        }
    }

    public static StackitEntity mapCostToEntity(final CostProjectDto dto, final String fromDate, final String toDate) {
        if (dto == null) {
            return null;
        }
        final StackitEntity entity = new StackitEntity();
        final String resourceId = String.format("cost-%s-%s", dto.getProjectId(), fromDate != null ? fromDate : "current");
        final UUID uuid = UUID.nameUUIDFromBytes(resourceId.getBytes());
        entity.setId(uuid);
        entity.setResourceId(resourceId);
        entity.setName(dto.getProjectName() != null && !dto.getProjectName().isBlank() ? dto.getProjectName() : dto.getProjectId());
        entity.setType(StackitConstants.RESOURCE_TYPE_BILLING);
        entity.setStatus(StackitConstants.STATUS_ACTIVE);
        entity.setRegion(StackitConstants.GLOBAL_REGION);
        entity.setProjectId(dto.getProjectId());

        // Cost API totalCharge is in cents -> convert to EUR
        final double amountEur = dto.getTotalCharge() != null ? dto.getTotalCharge() / 100.0 : 0.0;
        final double discountEur = dto.getTotalDiscount() != null ? dto.getTotalDiscount() / 100.0 : 0.0;

        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        final java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("amount", amountEur);
        data.put("currency", "EUR");
        data.put("totalChargeCents", dto.getTotalCharge() != null ? dto.getTotalCharge() : 0.0);
        data.put("totalDiscountCents", dto.getTotalDiscount() != null ? dto.getTotalDiscount() : 0.0);
        data.put("discountEur", discountEur);
        if (dto.getCustomerAccountId() != null) {
            data.put("customerAccountId", dto.getCustomerAccountId());
        }
        if (fromDate != null) {
            data.put("from", fromDate);
        }
        if (toDate != null) {
            data.put("to", toDate);
        }
        entity.setData(data);
        return entity;
    }
}
