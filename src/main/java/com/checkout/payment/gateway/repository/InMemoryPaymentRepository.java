package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.entity.PaymentEntity;
import com.checkout.payment.gateway.mapper.PaymentEntityMapper;
import com.checkout.payment.gateway.model.Payment;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryPaymentRepository implements PaymentRepository {

  private final ConcurrentHashMap<UUID, PaymentEntity> payments = new ConcurrentHashMap<>();
  private final PaymentEntityMapper entityMapper;

  public InMemoryPaymentRepository(PaymentEntityMapper entityMapper) {
    this.entityMapper = entityMapper;
  }

  @Override
  public void save(Payment payment) {
    PaymentEntity entity = entityMapper.toEntity(payment);
    payments.put(entity.getId(), entity);
  }

  @Override
  public Optional<Payment> findById(UUID id) {
    return Optional.ofNullable(payments.get(id)).map(entityMapper::toDomain);
  }
}
