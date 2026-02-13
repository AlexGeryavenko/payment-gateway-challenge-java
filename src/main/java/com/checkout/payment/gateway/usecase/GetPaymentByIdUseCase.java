package com.checkout.payment.gateway.usecase;

import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.metrics.PaymentMetrics;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.repository.PaymentRepository;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class GetPaymentByIdUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(GetPaymentByIdUseCase.class);

  private final PaymentRepository paymentRepository;
  private final PaymentMetrics paymentMetrics;
  private final ObservationRegistry observationRegistry;

  public GetPaymentByIdUseCase(PaymentRepository paymentRepository,
      PaymentMetrics paymentMetrics, ObservationRegistry observationRegistry) {
    this.paymentRepository = paymentRepository;
    this.paymentMetrics = paymentMetrics;
    this.observationRegistry = observationRegistry;
  }

  public Payment execute(UUID id) {
    return Observation.createNotStarted("find-payment", observationRegistry)
        .observe(() -> doExecute(id));
  }

  private Payment doExecute(UUID id) {
    try {
      MDC.put("paymentId", id.toString());
      LOG.debug("Requesting access to payment with ID {}", id);

      Optional<Payment> result = paymentRepository.findById(id);
      boolean found = result.isPresent();
      paymentMetrics.recordPaymentRetrieved(found);

      return result
          .orElseThrow(() -> new EventProcessingException("Invalid ID"));
    } finally {
      MDC.remove("paymentId");
    }
  }
}
