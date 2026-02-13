package com.checkout.payment.gateway.usecase;

import com.checkout.payment.gateway.client.BankPaymentAdapter;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentStatus;
import com.checkout.payment.gateway.repository.PaymentRepository;
import com.checkout.payment.gateway.validation.PaymentValidator;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProcessPaymentUseCase {

  private final PaymentRepository paymentRepository;
  private final PaymentValidator paymentValidator;
  private final BankPaymentAdapter bankPaymentAdapter;

  public ProcessPaymentUseCase(PaymentRepository paymentRepository,
      PaymentValidator paymentValidator, BankPaymentAdapter bankPaymentAdapter) {
    this.paymentRepository = paymentRepository;
    this.paymentValidator = paymentValidator;
    this.bankPaymentAdapter = bankPaymentAdapter;
  }

  public Payment execute(Payment payment) {
    if (!paymentValidator.isValid(payment)) {
      payment.setStatus(PaymentStatus.REJECTED);
      return payment;
    }

    payment.setId(UUID.randomUUID());
    payment.setCardNumberLastFour(
        payment.getCardNumber().substring(payment.getCardNumber().length() - 4));

    PaymentStatus status = bankPaymentAdapter.authorize(payment);
    payment.setStatus(status);

    paymentRepository.save(payment);

    return payment;
  }
}
