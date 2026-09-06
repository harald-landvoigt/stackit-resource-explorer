package com.landvoigtit.stackit.resourceexplorer;

import com.landvoigtit.stackit.resourceexplorer.billing.BillingInvoiceDto;
import com.landvoigtit.stackit.resourceexplorer.billing.BillingMapper;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import static org.junit.jupiter.api.Assertions.*;

public class BillingMapperTest {

    @Test
    public final void testInvoiceMappingToDto() {
        final String rawJson = "{\n" +
                "  \"id\": \"inv-123\",\n" +
                "  \"invoiceNumber\": \"INV-2026-001\",\n" +
                "  \"amount\": 1250.50,\n" +
                "  \"currency\": \"EUR\",\n" +
                "  \"status\": \"PAID\",\n" +
                "  \"billingDate\": \"2026-08-01T00:00:00Z\"\n" +
                "}";
        
        final BillingInvoiceDto dto = BillingMapper.mapToDto(rawJson);
        assertNotNull(dto);
        assertEquals("inv-123", dto.getId());
        assertEquals("INV-2026-001", dto.getInvoiceNumber());
        assertEquals(1250.50, dto.getAmount());
        assertEquals("EUR", dto.getCurrency());
        assertEquals("PAID", dto.getStatus());
        assertNotNull(dto.getBillingDate());
    }

    @Test
    public final void testInvoiceMappingToEntity() {
        final BillingInvoiceDto dto = new BillingInvoiceDto();
        dto.setId("inv-123");
        dto.setInvoiceNumber("INV-2026-001");
        dto.setAmount(1250.50);
        dto.setCurrency("EUR");
        dto.setStatus("PAID");
        dto.setBillingDate(OffsetDateTime.parse("2026-08-01T00:00:00Z"));

        final StackitEntity entity = BillingMapper.mapToEntity(dto);
        assertNotNull(entity);
        assertEquals("inv-123", entity.getResourceId());
        assertEquals("INV-2026-001", entity.getName());
        assertEquals("billing", entity.getType());
        assertEquals("PAID", entity.getStatus());
        assertEquals(1250.50, entity.getData().get("amount"));
        assertEquals("EUR", entity.getData().get("currency"));
    }

    @Test
    public final void testCostMappingToDto() {
        final String rawJson = "{\n" +
                "  \"customerAccountId\": \"org-123\",\n" +
                "  \"projectId\": \"proj-456\",\n" +
                "  \"projectName\": \"production-proj\",\n" +
                "  \"totalCharge\": 125050.0,\n" +
                "  \"totalDiscount\": 5000.0\n" +
                "}";

        final com.landvoigtit.stackit.resourceexplorer.billing.CostProjectDto dto = BillingMapper.mapCostDto(rawJson);
        assertNotNull(dto);
        assertEquals("org-123", dto.getCustomerAccountId());
        assertEquals("proj-456", dto.getProjectId());
        assertEquals("production-proj", dto.getProjectName());
        assertEquals(125050.0, dto.getTotalCharge());
        assertEquals(5000.0, dto.getTotalDiscount());
    }

    @Test
    public final void testCostMappingToEntity() {
        final com.landvoigtit.stackit.resourceexplorer.billing.CostProjectDto dto =
                new com.landvoigtit.stackit.resourceexplorer.billing.CostProjectDto(
                        "org-123",
                        "proj-456",
                        "production-proj",
                        125050.0,
                        5000.0
                );

        final StackitEntity entity = BillingMapper.mapCostToEntity(dto, "2026-09-01", "2026-09-30");
        assertNotNull(entity);
        assertEquals("cost-proj-456-2026-09-01", entity.getResourceId());
        assertEquals("production-proj", entity.getName());
        assertEquals("billing", entity.getType());
        assertEquals("ACTIVE", entity.getStatus());
        assertEquals("proj-456", entity.getProjectId());
        assertEquals(1250.50, entity.getData().get("amount")); // 125050 cents / 100
        assertEquals("EUR", entity.getData().get("currency"));
        assertEquals(125050.0, entity.getData().get("totalChargeCents"));
        assertEquals("org-123", entity.getData().get("customerAccountId"));
        assertEquals("2026-09-01", entity.getData().get("from"));
        assertEquals("2026-09-30", entity.getData().get("to"));
    }
}
