package com.checkout.payment.gateway.exception;

public class PaymentValidationException extends RuntimeException {

  private final String field;

  public PaymentValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String getField() {
    return field;
  }
}
