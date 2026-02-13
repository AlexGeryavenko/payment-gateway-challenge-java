package com.checkout.payment.gateway.mapper;

import com.checkout.payment.gateway.entity.PaymentEntity;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentEntityMapper {

  PaymentEntity toEntity(Payment payment);

  @Mapping(target = "cardNumber", ignore = true)
  @Mapping(target = "cvv", ignore = true)
  Payment toDomain(PaymentEntity entity);

  default String map(PaymentStatus status) {
    return status.name();
  }

  default PaymentStatus map(String status) {
    return PaymentStatus.valueOf(status);
  }
}
