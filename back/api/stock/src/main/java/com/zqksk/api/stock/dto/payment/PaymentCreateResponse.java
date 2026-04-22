package com.zqksk.api.stock.dto.payment;

public record PaymentCreateResponse(
    String orderId,
    int amount,
    String orderName
) {
}
