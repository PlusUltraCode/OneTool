package com.onetool.server.api.payments.business;

import com.onetool.server.api.order.Orders;
import com.onetool.server.api.order.service.OrderService;
import com.onetool.server.api.payments.domain.Payment;
import com.onetool.server.api.payments.dto.PaymentRequest;
import com.onetool.server.api.payments.dto.PaymentResponse;
import com.onetool.server.api.payments.service.PaymentService;
import com.onetool.server.global.annotation.Business;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Business
@Slf4j
@RequiredArgsConstructor
public class PaymentBusiness {

    private final PaymentService paymentService;
    private final OrderService orderService;

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentList(Long userId) {
        List<Orders> ordersList = orderService.findByUserId(userId);
        return ordersList.stream()
                .map(paymentService::findByOrders)
                .filter(Objects::nonNull)
                .map(PaymentResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public Long createPayment(PaymentRequest request) {
        Payment payment = request.toEntity(request, orderService);
        Payment savedPayment = paymentService.save(payment); // save()만 PaymentService
        return savedPayment.getId();
    }

    @Transactional
    public void deletePayment(Long paymentId) {
        Payment targetPayment = paymentService.findById(paymentId);
        paymentService.deleteById(targetPayment);
    }
}
