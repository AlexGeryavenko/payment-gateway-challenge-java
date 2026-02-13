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
    Payment payment = validPayment();
    payment.setExpiryMonth(7);
    payment.setExpiryYear(2025);

    assertTrue(validator.isValid(payment));
  }

  @Test
  void isValid_pastExpiry_returnsFalse() {
    Payment payment = validPayment();
    payment.setExpiryMonth(5);
    payment.setExpiryYear(2025);

    assertFalse(validator.isValid(payment));
  }

  @Test
  void isValid_currentMonth_returnsFalse() {
    Payment payment = validPayment();
    payment.setExpiryMonth(6);
    payment.setExpiryYear(2025);

    assertFalse(validator.isValid(payment));
  }

  @Test
  void isValid_luhnValidCard_returnsTrue() {
    Payment payment = validPayment();
    payment.setExpiryMonth(7);
    payment.setExpiryYear(2025);

    assertTrue(validator.isValid(payment));
  }

  @Test
  void isValid_luhnInvalidCard_returnsFalse() {
    Payment payment = validPayment();
    payment.setCardNumber("11111111111111");
    payment.setExpiryMonth(7);
    payment.setExpiryYear(2025);

    assertFalse(validator.isValid(payment));
  }

  @Test
  void isCardNumberValid_luhnValidCard_returnsTrue() {
    Payment payment = validPayment();

    assertTrue(validator.isCardNumberValid(payment));
  }

  @Test
  void isCardNumberValid_luhnInvalidCard_returnsFalse() {
    Payment payment = validPayment();
    payment.setCardNumber("11111111111111");

    assertFalse(validator.isCardNumberValid(payment));
  }

  @Test
  void isLuhnValid_wellKnownTestCards() {
    assertTrue(PaymentValidator.isLuhnValid("4111111111111111"));
    assertTrue(PaymentValidator.isLuhnValid("2222405343248877"));
    assertTrue(PaymentValidator.isLuhnValid("5500000000000004"));
    assertFalse(PaymentValidator.isLuhnValid("1234567890123456"));
    assertFalse(PaymentValidator.isLuhnValid("11111111111111"));
  }

  @Test
  void isLuhnValid_nullOrEmpty_returnsFalse() {
    assertFalse(PaymentValidator.isLuhnValid(null));
    assertFalse(PaymentValidator.isLuhnValid(""));
  }

  private Payment validPayment() {
    Payment payment = new Payment();
    payment.setCardNumber("2222405343248877");
    return payment;
  }
}
