package com.checkout.payment.gateway.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.checkout.payment.gateway.api.model.ErrorResponse;
import com.checkout.payment.gateway.api.model.ValidationErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

class CommonExceptionHandlerTest {

  private final CommonExceptionHandler handler = new CommonExceptionHandler();

  @BeforeEach
  void setUp() {
    MDC.put("correlationId", "test-correlation-id");
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void handleUnexpectedException_returnsInternalServerErrorWithGenericMessage() {
    ResponseEntity<ErrorResponse> response =
        handler.handleUnexpectedException(new RuntimeException("boom"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals("An unexpected error occurred. Please try again later.",
        response.getBody().getMessage());
    assertEquals("test-correlation-id", response.getBody().getCorrelationId());
    assertNotNull(response.getBody().getTimestamp());
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

  @Test
  void handleBankCommunicationException_includesCorrelationIdAndTimestamp() {
    BankCommunicationException ex =
        new BankCommunicationException("Bank failed", new RuntimeException("timeout"));

    ResponseEntity<ErrorResponse> response =
        handler.handleBankCommunicationException(ex);

    assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
    assertEquals("Bank service unavailable", response.getBody().getMessage());
    assertEquals("test-correlation-id", response.getBody().getCorrelationId());
    assertNotNull(response.getBody().getTimestamp());
  }

  @Test
  void handleException_notFound_includesCorrelationIdAndTimestamp() {
    EventProcessingException ex = new EventProcessingException("not found");

    ResponseEntity<ErrorResponse> response = handler.handleException(ex);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals("Page not found", response.getBody().getMessage());
    assertEquals("test-correlation-id", response.getBody().getCorrelationId());
    assertNotNull(response.getBody().getTimestamp());
  }
}
