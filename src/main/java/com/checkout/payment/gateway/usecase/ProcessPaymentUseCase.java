package com.checkout.payment.gateway.usecase;

import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.repository.PaymentRepository;
import com.checkout.payment.gateway.validation.PaymentValidator;
import org.springframework.stereotype.Service;

@Service
public class ProcessPaymentUseCase {

  private final PaymentRepository paymentRepository;
  private final PaymentValidator paymentValidator;

  public ProcessPaymentUseCase(PaymentRepository paymentRepository,
      PaymentValidator paymentValidator) {
    this.paymentRepository = paymentRepository;
    this.paymentValidator = paymentValidator;
  }

  public Payment execute(Payment payment) {
    // TODO: validate, call bank, store result
    return payment;
  }
}
