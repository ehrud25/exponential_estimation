package com.zqksk.api.stock.controller;

import com.zqksk.api.stock.dto.payment.PaymentConfirmRequest;
import com.zqksk.api.stock.dto.payment.PaymentCreateResponse;
import com.zqksk.api.stock.service.TossPaymentsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class PaymentController {

    private static final int ANALYSIS_PAYMENT_AMOUNT = 1;
    private static final String ORDER_NAME = "종목 분석 1회 이용권";

    private final TossPaymentsService tossPaymentsService;

    @PostMapping("/payment/create")
    public ResponseEntity<PaymentCreateResponse> createPayment() {
        String orderId = "analysis-" + UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        return ResponseEntity.ok(new PaymentCreateResponse(orderId, ANALYSIS_PAYMENT_AMOUNT, ORDER_NAME));
    }

    @PostMapping("/payment/confirm")
    public ResponseEntity<Void> confirmPayment(@Valid @RequestBody PaymentConfirmRequest request) {
        if (request.amount() != ANALYSIS_PAYMENT_AMOUNT) {
            return ResponseEntity.badRequest().build();
        }

        tossPaymentsService.confirm(request.paymentKey(), request.orderId(), request.amount());
        return ResponseEntity.ok().build();
    }
}
