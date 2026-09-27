package com.example.virtualthreads.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record OrderRequest(
        @NotBlank(message = "Order ID cannot be blank")
        String orderId,

        @NotBlank(message = "Customer ID cannot be blank")
        String customerId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        List<String> itemIds
) {
}
