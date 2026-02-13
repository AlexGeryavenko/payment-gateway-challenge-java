package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.client.bank.api.DefaultApi;
import com.checkout.payment.gateway.client.bank.model.BankPaymentRequest;
import com.checkout.payment.gateway.client.bank.model.BankPaymentResponse;
import com.checkout.payment.gateway.exception.BankCommunicationException;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;

@Component
public class BankPaymentAdapter {

  private final DefaultApi bankApi;

  public BankPaymentAdapter(DefaultApi bankApi) {
    this.bankApi = bankApi;
  }

  @CircuitBreaker(name = "bankClient")
  public PaymentStatus authorize(Payment payment) {
    BankPaymentRequest request = new BankPaymentRequest()
        .cardNumber(payment.getCardNumber())
        .expiryDate(String.format("%02d/%d", payment.getExpiryMonth(), payment.getExpiryYear()))
        .currency(payment.getCurrency())
        .amount(payment.getAmount())
        .cvv(payment.getCvv());
    try {
      BankPaymentResponse response = bankApi.authorizePayment(request);
      return Boolean.TRUE.equals(response.getAuthorized())
          ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;
    } catch (Exception ex) {
      throw new BankCommunicationException("Bank communication failed", ex);
    }
  }
}
