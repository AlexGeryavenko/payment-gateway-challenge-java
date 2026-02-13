package com.checkout.payment.gateway.model;

import java.util.UUID;

public class Payment {

  private UUID id;
  private PaymentStatus status;
  private String cardNumber;
  private String cardNumberLastFour;
  private int expiryMonth;
  private int expiryYear;
  private String currency;
  private int amount;
  private String cvv;
  private String idempotencyKey;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public void setStatus(PaymentStatus status) {
    this.status = status;
  }

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public String getCardNumberLastFour() {
    return cardNumberLastFour;
  }

  public void setCardNumberLastFour(String cardNumberLastFour) {
    this.cardNumberLastFour = cardNumberLastFour;
  }

  public int getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(int expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public int getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(int expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public int getAmount() {
    return amount;
  }

  public void setAmount(int amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  @Override
  public String toString() {
    String maskedCardNumber = cardNumber != null
        ? "*".repeat(cardNumber.length() - 4) + cardNumberLastFour
        : "null";
    return "Payment{"
        + "id=" + id
        + ", status=" + status
        + ", cardNumber='" + maskedCardNumber + '\''
        + ", cardNumberLastFour='" + cardNumberLastFour + '\''
        + ", expiryMonth=" + expiryMonth
        + ", expiryYear=" + expiryYear
        + ", currency='" + currency + '\''
        + ", amount=" + amount
        + ", cvv='***'"
        + '}';
  }
}
