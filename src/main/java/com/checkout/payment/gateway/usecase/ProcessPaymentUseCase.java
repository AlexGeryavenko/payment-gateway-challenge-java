package com.checkout.payment.gateway.usecase;

import static com.checkout.payment.gateway.exception.ValidationErrors.CARD_NUMBER_INVALID_LUHN;
import static com.checkout.payment.gateway.exception.ValidationErrors.EXPIRY_DATE_IN_FUTURE;
import static com.checkout.payment.gateway.exception.ValidationErrors.FIELD_CARD_NUMBER;
import static com.checkout.payment.gateway.exception.ValidationErrors.FIELD_EXPIRY_DATE;

import com.checkout.payment.gateway.client.BankPaymentAdapter;
import com.checkout.payment.gateway.exception.PaymentValidationException;
import com.checkout.payment.gateway.metrics.PaymentMetrics;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentStatus;
import com.checkout.payment.gateway.repository.PaymentRepository;
import com.checkout.payment.gateway.validation.PaymentValidator;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Optional;
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
      if (payment.getIdempotencyKey() != null) {
        Optional<Payment> existing =
            paymentRepository.findByIdempotencyKey(payment.getIdempotencyKey());
        if (existing.isPresent()) {
          LOG.info("Idempotent request — returning cached response for key={}",
              payment.getIdempotencyKey());
          return existing.get();
        }
      }

      Observation.createNotStarted("validate-payment", observationRegistry)
          .observe(() -> {
            if (!paymentValidator.isCardNumberValid(payment)) {
              LOG.info("Payment rejected — Luhn check failed");
              paymentMetrics.recordPaymentProcessed(
                  PaymentStatus.REJECTED.name(), payment.getCurrency());
              throw new PaymentValidationException(
                  FIELD_CARD_NUMBER, CARD_NUMBER_INVALID_LUHN);
            }
            if (!paymentValidator.isValid(payment)) {
              LOG.info("Payment rejected — validation failed");
              paymentMetrics.recordPaymentProcessed(
                  PaymentStatus.REJECTED.name(), payment.getCurrency());
              throw new PaymentValidationException(
                  FIELD_EXPIRY_DATE, EXPIRY_DATE_IN_FUTURE);
            }
            return null;
          });

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
