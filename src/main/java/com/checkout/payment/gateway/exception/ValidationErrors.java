package com.checkout.payment.gateway.exception;

public final class ValidationErrors {

  public static final String VALIDATION_FAILED = "Validation failed";
  public static final String MALFORMED_JSON = "Malformed JSON request body";

  // Field names (match the ProcessPaymentRequest property names)
  public static final String FIELD_REQUEST_BODY = "requestBody";
  public static final String FIELD_CARD_NUMBER = "cardNumber";
  public static final String FIELD_EXPIRY_DATE = "expiryDate";
  public static final String FIELD_CURRENCY = "currency";

  // Error descriptions
  public static final String CARD_NUMBER_INVALID_LUHN =
      "Card number failed Luhn check";
  public static final String EXPIRY_DATE_IN_FUTURE =
      "Card expiry date must be in the future";
  public static final String CURRENCY_INVALID =
      "Invalid value. Accepted values are: GBP, USD, EUR";

  private ValidationErrors() {
  }
}
