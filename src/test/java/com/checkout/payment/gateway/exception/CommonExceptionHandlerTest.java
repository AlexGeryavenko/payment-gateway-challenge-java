package com.checkout.payment.gateway.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.checkout.payment.gateway.api.model.ErrorResponse;
import com.checkout.payment.gateway.api.model.ValidationErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

class CommonExceptionHandlerTest {

  private final CommonExceptionHandler handler = new CommonExceptionHandler();

  @Test
  void handleUnexpectedException_returnsInternalServerErrorWithGenericMessage() {
    ResponseEntity<ErrorResponse> response =
        handler.handleUnexpectedException(new RuntimeException("boom"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals("An unexpected error occurred. Please try again later.",
        response.getBody().getMessage());
  }

  @Test
  void handlePaymentValidationException_returns400WithFieldError() {
    PaymentValidationException ex =
        new PaymentValidationException("expiryDate", "Card expiry date must be in the future");

    ResponseEntity<ValidationErrorResponse> response =
        handler.handlePaymentValidationException(ex);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ValidationErrorResponse body = response.getBody();
    assertNotNull(body);
    assertEquals("Rejected", body.getStatus().getValue());
    assertEquals("Validation failed", body.getMessage());
    assertEquals(1, body.getErrors().size());
    assertEquals("expiryDate", body.getErrors().get(0).getField());
    assertEquals("Card expiry date must be in the future",
        body.getErrors().get(0).getMessage());
  }

  @Test
  void handleMessageNotReadableException_malformedJson_returnsRequestBodyField() {
    HttpMessageNotReadableException ex =
        new HttpMessageNotReadableException("JSON parse error", (Throwable) null, null);

    ResponseEntity<ValidationErrorResponse> response =
        handler.handleMessageNotReadableException(ex);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ValidationErrorResponse body = response.getBody();
    assertNotNull(body);
    assertEquals("Rejected", body.getStatus().getValue());
    assertEquals("Validation failed", body.getMessage());
    assertEquals(1, body.getErrors().size());
    assertEquals("requestBody", body.getErrors().get(0).getField());
    assertEquals("Malformed JSON request body", body.getErrors().get(0).getMessage());
  }
}
