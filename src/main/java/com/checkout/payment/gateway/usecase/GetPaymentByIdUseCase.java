package com.checkout.payment.gateway.usecase;

import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.repository.PaymentRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GetPaymentByIdUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(GetPaymentByIdUseCase.class);

  private final PaymentRepository paymentRepository;

  public GetPaymentByIdUseCase(PaymentRepository paymentRepository) {
    this.paymentRepository = paymentRepository;
  }

  public Payment execute(UUID id) {
    LOG.debug("Requesting access to payment with ID {}", id);
    return paymentRepository.findById(id)
        .orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }
}
