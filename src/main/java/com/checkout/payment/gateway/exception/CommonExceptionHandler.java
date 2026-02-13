package com.checkout.payment.gateway.exception;

import static com.checkout.payment.gateway.exception.ValidationErrors.CURRENCY_INVALID;
import static com.checkout.payment.gateway.exception.ValidationErrors.FIELD_CURRENCY;
import static com.checkout.payment.gateway.exception.ValidationErrors.FIELD_REQUEST_BODY;
import static com.checkout.payment.gateway.exception.ValidationErrors.MALFORMED_JSON;
import static com.checkout.payment.gateway.exception.ValidationErrors.VALIDATION_FAILED;

import com.checkout.payment.gateway.api.model.ErrorResponse;
import com.checkout.payment.gateway.api.model.FieldError;
import com.checkout.payment.gateway.api.model.ValidationErrorResponse;
import com.checkout.payment.gateway.api.model.ValidationErrorResponse.StatusEnum;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
    return new ResponseEntity<>(errorResponse("Page not found"), HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(CallNotPermittedException.class)
  public ResponseEntity<ErrorResponse> handleCircuitBreakerOpen(CallNotPermittedException ex) {
    LOG.warn("Circuit breaker open: {}", ex.getMessage());
    return new ResponseEntity<>(errorResponse("Bank service unavailable"),
        HttpStatus.BAD_GATEWAY);
  }

  @ExceptionHandler(BankCommunicationException.class)
  public ResponseEntity<ErrorResponse> handleBankCommunicationException(
      BankCommunicationException ex) {
    LOG.error("Bank communication failed", ex);
    return new ResponseEntity<>(errorResponse("Bank service unavailable"),
        HttpStatus.BAD_GATEWAY);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ValidationErrorResponse> handleValidationException(
      MethodArgumentNotValidException ex) {
    LOG.warn("Validation failed: {}", ex.getMessage());

    List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> new FieldError().field(fe.getField()).message(fe.getDefaultMessage()))
        .toList();

    return new ResponseEntity<>(validationError(fieldErrors), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ValidationErrorResponse> handleMessageNotReadableException(
      HttpMessageNotReadableException ex) {
    LOG.warn("Malformed request: {}", ex.getMessage());

    FieldError fieldError;
    if (ex.getCause() instanceof JsonMappingException jme
        && jme.getPath() != null && !jme.getPath().isEmpty()) {
      String fieldName = jme.getPath().get(0).getFieldName();
      if ("currency".equals(fieldName)) {
        fieldError = new FieldError().field(FIELD_CURRENCY).message(CURRENCY_INVALID);
      } else if (ex.getCause() instanceof InvalidFormatException ife) {
        fieldError = new FieldError().field(fieldName).message(ife.getOriginalMessage());
      } else {
        fieldError = new FieldError().field(fieldName).message(jme.getOriginalMessage());
      }
    } else {
      fieldError = new FieldError().field(FIELD_REQUEST_BODY).message(MALFORMED_JSON);
    }

    return new ResponseEntity<>(validationError(List.of(fieldError)), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(PaymentValidationException.class)
  public ResponseEntity<ValidationErrorResponse> handlePaymentValidationException(
      PaymentValidationException ex) {
    LOG.warn("Payment validation failed: field={}, message={}", ex.getField(), ex.getMessage());

    FieldError fieldError = new FieldError().field(ex.getField()).message(ex.getMessage());
    return new ResponseEntity<>(validationError(List.of(fieldError)), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatchException(
      MethodArgumentTypeMismatchException ex) {
    LOG.warn("Invalid request parameter: {}", ex.getMessage());
    return new ResponseEntity<>(errorResponse("Invalid request parameter"),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {
    LOG.error("Unexpected error occurred", ex);
    return new ResponseEntity<>(
        errorResponse("An unexpected error occurred. Please try again later."),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private static ErrorResponse errorResponse(String message) {
    return new ErrorResponse()
        .message(message)
        .correlationId(MDC.get("correlationId"))
        .timestamp(OffsetDateTime.now());
  }

  private static ValidationErrorResponse validationError(List<FieldError> errors) {
    return new ValidationErrorResponse()
        .status(StatusEnum.REJECTED)
        .message(VALIDATION_FAILED)
        .errors(errors);
  }
}
