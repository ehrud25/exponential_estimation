package com.zqksk.api.stock.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentConfirmRequest(
    @NotBlank(message = "paymentKey는 필수입니다")
    String paymentKey,
    @NotBlank(message = "orderId는 필수입니다")
    String orderId,
    @NotNull(message = "amount는 필수입니다")
    @Positive(message = "amount는 양수여야 합니다")
    Integer amount
) {
}
