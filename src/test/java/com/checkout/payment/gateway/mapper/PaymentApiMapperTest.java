package com.checkout.payment.gateway.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.checkout.payment.gateway.api.model.PaymentDetailsResponse;
import com.checkout.payment.gateway.api.model.ProcessPaymentRequest;
import com.checkout.payment.gateway.api.model.ProcessPaymentRequest.CurrencyEnum;
import com.checkout.payment.gateway.api.model.ProcessPaymentResponse;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class PaymentApiMapperTest {

  private final PaymentApiMapper mapper = Mappers.getMapper(PaymentApiMapper.class);

  @Test
  void toDomain_mapsAllRequestFields() {
    ProcessPaymentRequest request = new ProcessPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryMonth(4);
    request.setExpiryYear(2027);
    request.setCurrency(CurrencyEnum.GBP);
    request.setAmount(100);
    request.setCvv("123");

    Payment payment = mapper.toDomain(request);

    assertEquals("2222405343248877", payment.getCardNumber());
    assertEquals(4, payment.getExpiryMonth());
    assertEquals(2027, payment.getExpiryYear());
    assertEquals("GBP", payment.getCurrency());
    assertEquals(100, payment.getAmount());
    assertEquals("123", payment.getCvv());
  }

  @Test
  void toDomain_ignoresIdStatusAndCardNumberLastFour() {
    ProcessPaymentRequest request = new ProcessPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryMonth(4);
    request.setExpiryYear(2027);
    request.setCurrency(CurrencyEnum.USD);
    request.setAmount(100);
    request.setCvv("123");

    Payment payment = mapper.toDomain(request);

    assertNull(payment.getId());
    assertNull(payment.getStatus());
    assertNull(payment.getCardNumberLastFour());
  }

  @Test
  void toDomain_convertsCurrencyEnumToString() {
    ProcessPaymentRequest request = new ProcessPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryMonth(4);
    request.setExpiryYear(2027);
    request.setCurrency(CurrencyEnum.GBP);
    request.setAmount(100);
    request.setCvv("123");

    Payment payment = mapper.toDomain(request);

    assertEquals("GBP", payment.getCurrency());
  }

  @Test
  void toProcessResponse_mapsAllPaymentFields() {
    Payment payment = new Payment();
    payment.setId(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"));
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setCardNumberLastFour("8877");
    payment.setExpiryMonth(4);
    payment.setExpiryYear(2027);
    payment.setCurrency("GBP");
    payment.setAmount(100);

    ProcessPaymentResponse response = mapper.toProcessResponse(payment);

    assertEquals(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"), response.getId());
    assertEquals("8877", response.getCardNumberLastFour());
    assertEquals(4, response.getExpiryMonth());
    assertEquals(2027, response.getExpiryYear());
    assertEquals("GBP", response.getCurrency());
    assertEquals(100, response.getAmount());
  }

  @Test
  void toProcessResponse_mapsStatusCorrectly() {
    Payment payment = new Payment();
    payment.setStatus(PaymentStatus.AUTHORIZED);

    ProcessPaymentResponse response = mapper.toProcessResponse(payment);

    assertEquals(ProcessPaymentResponse.StatusEnum.AUTHORIZED, response.getStatus());
  }

  @Test
  void toDetailsResponse_mapsAuthorizedPayment() {
    Payment payment = new Payment();
    payment.setId(UUID.randomUUID());
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setCardNumberLastFour("8877");
    payment.setExpiryMonth(4);
    payment.setExpiryYear(2027);
    payment.setCurrency("GBP");
    payment.setAmount(100);

    PaymentDetailsResponse response = mapper.toDetailsResponse(payment);

    assertEquals(PaymentDetailsResponse.StatusEnum.AUTHORIZED, response.getStatus());
    assertEquals("8877", response.getCardNumberLastFour());
    assertEquals(4, response.getExpiryMonth());
    assertEquals(2027, response.getExpiryYear());
    assertEquals("GBP", response.getCurrency());
    assertEquals(100, response.getAmount());
  }

  @Test
  void toDetailsResponse_throwsForRejectedPayment() {
    Payment payment = new Payment();
    payment.setStatus(PaymentStatus.REJECTED);

    assertThrows(IllegalArgumentException.class, () -> mapper.toDetailsResponse(payment));
  }
}
