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
    return YearMonth.of(payment.getExpiryYear(), payment.getExpiryMonth())
        .isAfter(YearMonth.now(clock));
  }
}
