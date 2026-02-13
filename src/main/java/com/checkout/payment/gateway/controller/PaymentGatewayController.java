package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.api.model.ProcessPaymentRequest;
import com.checkout.payment.gateway.api.model.ProcessPaymentResponse;
import com.checkout.payment.gateway.mapper.PaymentApiMapper;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentStatus;
import com.checkout.payment.gateway.usecase.GetPaymentByIdUseCase;
import com.checkout.payment.gateway.usecase.ProcessPaymentUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("api")
public class PaymentGatewayController {

  private final GetPaymentByIdUseCase getPaymentByIdUseCase;
  private final ProcessPaymentUseCase processPaymentUseCase;
  private final PaymentApiMapper apiMapper;

  public PaymentGatewayController(GetPaymentByIdUseCase getPaymentByIdUseCase,
      ProcessPaymentUseCase processPaymentUseCase, PaymentApiMapper apiMapper) {
    this.getPaymentByIdUseCase = getPaymentByIdUseCase;
    this.processPaymentUseCase = processPaymentUseCase;
    this.apiMapper = apiMapper;
  }

  @GetMapping("/v1/payment/{id}")
  public ResponseEntity<ProcessPaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    Payment payment = getPaymentByIdUseCase.execute(id);
    return new ResponseEntity<>(apiMapper.toProcessResponse(payment), HttpStatus.OK);
  }

  @PostMapping("/v1/payment")
  public ResponseEntity<ProcessPaymentResponse> processPayment(
      @Valid @RequestBody ProcessPaymentRequest request) {
    Payment payment = apiMapper.toDomain(request);
    Payment result = processPaymentUseCase.execute(payment);
    ProcessPaymentResponse response = apiMapper.toProcessResponse(result);
    HttpStatus status = result.getStatus() == PaymentStatus.REJECTED
        ? HttpStatus.BAD_REQUEST : HttpStatus.OK;
    return new ResponseEntity<>(response, status);
  }
}
