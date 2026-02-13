package com.checkout.payment.gateway.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.checkout.payment.gateway.entity.PaymentEntity;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class PaymentEntityMapperTest {

  private final PaymentEntityMapper mapper = Mappers.getMapper(PaymentEntityMapper.class);

  @Test
  void toEntity_mapsFieldsAndStripsCardNumberAndCvv() {
    Payment payment = new Payment();
    payment.setId(UUID.randomUUID());
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setCardNumber("2222405343248877");
    payment.setCardNumberLastFour("8877");
    payment.setExpiryMonth(4);
    payment.setExpiryYear(2027);
    payment.setCurrency("GBP");
    payment.setAmount(100);
    payment.setCvv("123");

    PaymentEntity entity = mapper.toEntity(payment);

    assertEquals(payment.getId(), entity.getId());
    assertEquals("AUTHORIZED", entity.getStatus());
    assertEquals("8877", entity.getCardNumberLastFour());
    assertEquals(4, entity.getExpiryMonth());
    assertEquals(2027, entity.getExpiryYear());
    assertEquals("GBP", entity.getCurrency());
    assertEquals(100, entity.getAmount());
  }

  @Test
  void toDomain_mapsFieldsAndLeavesCardNumberCvvNull() {
    PaymentEntity entity = new PaymentEntity();
    entity.setId(UUID.randomUUID());
    entity.setStatus("DECLINED");
    entity.setCardNumberLastFour("9876");
    entity.setExpiryMonth(6);
    entity.setExpiryYear(2025);
    entity.setCurrency("USD");
    entity.setAmount(500);

    Payment payment = mapper.toDomain(entity);

    assertEquals(entity.getId(), payment.getId());
    assertEquals(PaymentStatus.DECLINED, payment.getStatus());
    assertEquals("9876", payment.getCardNumberLastFour());
    assertEquals(6, payment.getExpiryMonth());
    assertEquals(2025, payment.getExpiryYear());
    assertEquals("USD", payment.getCurrency());
    assertEquals(500, payment.getAmount());
    assertNull(payment.getCardNumber());
    assertNull(payment.getCvv());
  }

  @Test
  void statusRoundTrip_convertsCorrectly() {
    assertEquals("AUTHORIZED", mapper.map(PaymentStatus.AUTHORIZED));
    assertEquals(PaymentStatus.AUTHORIZED, mapper.map("AUTHORIZED"));
  }
}
