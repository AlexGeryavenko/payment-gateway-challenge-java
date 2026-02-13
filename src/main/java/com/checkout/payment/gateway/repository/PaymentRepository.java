package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.Payment;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

  void save(Payment payment);

  Optional<Payment> findById(UUID id);

  Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
