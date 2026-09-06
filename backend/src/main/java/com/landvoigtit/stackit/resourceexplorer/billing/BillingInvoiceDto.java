package com.landvoigtit.stackit.resourceexplorer.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
public class BillingInvoiceDto {

    @NotBlank
    private String id;

    @NotBlank
    private String invoiceNumber;

    @NotNull
    @PositiveOrZero
    private Double amount;

    @NotBlank
    private String currency;

    @NotBlank
    private String status;

    @NotNull
    private OffsetDateTime billingDate;
}
