package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.api.model.ProcessPaymentResponse;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentsRepository {

  private final HashMap<UUID, ProcessPaymentResponse> payments = new HashMap<>();

  public void add(ProcessPaymentResponse payment) {
    payments.put(payment.getId(), payment);
  }

  public Optional<ProcessPaymentResponse> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

}
