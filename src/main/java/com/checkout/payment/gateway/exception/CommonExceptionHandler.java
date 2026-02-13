package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.api.model.ErrorResponse;
import com.checkout.payment.gateway.api.model.ProcessPaymentResponse;
import com.checkout.payment.gateway.api.model.ProcessPaymentResponse.StatusEnum;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.error("Exception happened", ex);
    return new ResponseEntity<>(new ErrorResponse().message("Page not found"),
        HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(CallNotPermittedException.class)
  public ResponseEntity<ErrorResponse> handleCircuitBreakerOpen(CallNotPermittedException ex) {
    LOG.warn("Circuit breaker open: {}", ex.getMessage());
    return new ResponseEntity<>(new ErrorResponse().message("Bank service unavailable"),
        HttpStatus.BAD_GATEWAY);
  }

  @ExceptionHandler(BankCommunicationException.class)
  public ResponseEntity<ErrorResponse> handleBankCommunicationException(
      BankCommunicationException ex) {
    LOG.error("Bank communication failed", ex);
    return new ResponseEntity<>(new ErrorResponse().message("Bank service unavailable"),
        HttpStatus.BAD_GATEWAY);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProcessPaymentResponse> handleValidationException(
      MethodArgumentNotValidException ex) {
    LOG.warn("Validation failed: {}", ex.getMessage());
    return new ResponseEntity<>(new ProcessPaymentResponse().status(StatusEnum.REJECTED),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProcessPaymentResponse> handleMessageNotReadableException(
      HttpMessageNotReadableException ex) {
    LOG.warn("Malformed request: {}", ex.getMessage());
    return new ResponseEntity<>(new ProcessPaymentResponse().status(StatusEnum.REJECTED),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatchException(
      MethodArgumentTypeMismatchException ex) {
    LOG.warn("Invalid request parameter: {}", ex.getMessage());
    return new ResponseEntity<>(new ErrorResponse().message("Invalid request parameter"),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {
    LOG.error("Unexpected error occurred", ex);
    return new ResponseEntity<>(
        new ErrorResponse().message("An unexpected error occurred. Please try again later."),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
