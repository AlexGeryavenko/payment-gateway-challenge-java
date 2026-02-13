package com.checkout.payment.gateway.usecase;

import com.checkout.payment.gateway.client.BankPaymentAdapter;
import com.checkout.payment.gateway.metrics.PaymentMetrics;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentStatus;
import com.checkout.payment.gateway.repository.PaymentRepository;
import com.checkout.payment.gateway.validation.PaymentValidator;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class ProcessPaymentUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessPaymentUseCase.class);

  private final PaymentRepository paymentRepository;
  private final PaymentValidator paymentValidator;
  private final BankPaymentAdapter bankPaymentAdapter;
  private final PaymentMetrics paymentMetrics;
  private final ObservationRegistry observationRegistry;

  public ProcessPaymentUseCase(PaymentRepository paymentRepository,
      PaymentValidator paymentValidator, BankPaymentAdapter bankPaymentAdapter,
      PaymentMetrics paymentMetrics, ObservationRegistry observationRegistry) {
    this.paymentRepository = paymentRepository;
    this.paymentValidator = paymentValidator;
    this.bankPaymentAdapter = bankPaymentAdapter;
    this.paymentMetrics = paymentMetrics;
    this.observationRegistry = observationRegistry;
  }

  public Payment execute(Payment payment) {
    return Observation.createNotStarted("process-payment", observationRegistry)
        .observe(() -> doExecute(payment));
  }

  private Payment doExecute(Payment payment) {
    try {
      Boolean validResult = Observation
          .createNotStarted("validate-payment", observationRegistry)
          .observe(() -> paymentValidator.isValid(payment));
      boolean valid = Boolean.TRUE.equals(validResult);

      if (!valid) {
        LOG.info("Payment rejected — validation failed");
        payment.setStatus(PaymentStatus.REJECTED);
        paymentMetrics.recordPaymentProcessed(
            PaymentStatus.REJECTED.name(), payment.getCurrency());
        return payment;
      }

      payment.setId(UUID.randomUUID());
      MDC.put("paymentId", payment.getId().toString());

      payment.setCardNumberLastFour(
          payment.getCardNumber().substring(payment.getCardNumber().length() - 4));

      LOG.info("Requesting bank authorization");
      PaymentStatus bankResult = Observation
          .createNotStarted("bank-authorize", observationRegistry)
          .observe(() -> {
            try {
              return paymentMetrics.recordBankCallDuration(
                  () -> bankPaymentAdapter.authorize(payment));
            } catch (RuntimeException e) {
              throw e;
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      PaymentStatus status = bankResult != null ? bankResult : PaymentStatus.DECLINED;
      payment.setStatus(status);

      LOG.info("Payment {} — status={}", payment.getId(), status);
      paymentMetrics.recordPaymentProcessed(status.name(), payment.getCurrency());
      paymentMetrics.recordPaymentAmount(payment.getCurrency(), payment.getAmount());

      Observation.createNotStarted("save-payment", observationRegistry)
          .observe(() -> paymentRepository.save(payment));

      return payment;
    } finally {
      MDC.remove("paymentId");
    }
  }
}
