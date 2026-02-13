package com.checkout.payment.gateway.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.checkout.payment.gateway.api.model.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
}
