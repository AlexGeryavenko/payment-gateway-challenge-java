package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.api.model.ProcessPaymentResponse;
import com.checkout.payment.gateway.mapper.PaymentApiMapper;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.usecase.GetPaymentByIdUseCase;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController("api")
public class PaymentGatewayController {

  private final GetPaymentByIdUseCase getPaymentByIdUseCase;
  private final PaymentApiMapper apiMapper;

  public PaymentGatewayController(GetPaymentByIdUseCase getPaymentByIdUseCase,
      PaymentApiMapper apiMapper) {
    this.getPaymentByIdUseCase = getPaymentByIdUseCase;
    this.apiMapper = apiMapper;
  }

  @GetMapping("/payment/{id}")
  public ResponseEntity<ProcessPaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    Payment payment = getPaymentByIdUseCase.execute(id);
    return new ResponseEntity<>(apiMapper.toProcessResponse(payment), HttpStatus.OK);
  }
}
