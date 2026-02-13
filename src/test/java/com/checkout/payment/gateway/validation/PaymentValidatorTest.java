package com.checkout.payment.gateway.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.model.Payment;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PaymentValidatorTest {

  private final Clock fixedClock = Clock.fixed(
      Instant.parse("2025-06-15T00:00:00Z"), ZoneOffset.UTC);
  private final PaymentValidator validator = new PaymentValidator(fixedClock);

  @Test
  void isValid_futureExpiry_returnsTrue() {
    Payment payment = new Payment();
    payment.setExpiryMonth(7);
    payment.setExpiryYear(2025);

    assertTrue(validator.isValid(payment));
  }

  @Test
  void isValid_pastExpiry_returnsFalse() {
    Payment payment = new Payment();
    payment.setExpiryMonth(5);
    payment.setExpiryYear(2025);

    assertFalse(validator.isValid(payment));
  }

  @Test
  void isValid_currentMonth_returnsFalse() {
    Payment payment = new Payment();
    payment.setExpiryMonth(6);
    payment.setExpiryYear(2025);

    assertFalse(validator.isValid(payment));
  }
}
