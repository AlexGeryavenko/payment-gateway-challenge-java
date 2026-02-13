package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.model.Payment;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.stereotype.Component;

@Component
public class PaymentValidator {

  private final Clock clock;

  public PaymentValidator(Clock clock) {
    this.clock = clock;
  }

  public boolean isValid(Payment payment) {
    return isLuhnValid(payment.getCardNumber())
        && YearMonth.of(payment.getExpiryYear(), payment.getExpiryMonth())
            .isAfter(YearMonth.now(clock));
  }

  public boolean isCardNumberValid(Payment payment) {
    return isLuhnValid(payment.getCardNumber());
  }

  static boolean isLuhnValid(String cardNumber) {
    if (cardNumber == null || cardNumber.isEmpty()) {
      return false;
    }
    int sum = 0;
    boolean alternate = false;
    for (int i = cardNumber.length() - 1; i >= 0; i--) {
      int n = cardNumber.charAt(i) - '0';
      if (alternate) {
        n *= 2;
        if (n > 9) {
          n -= 9;
        }
      }
      sum += n;
      alternate = !alternate;
    }
    return sum % 10 == 0;
  }
}
