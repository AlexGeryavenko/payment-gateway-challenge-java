package com.checkout.payment.gateway.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MaskingConverterTest {

  @Test
  void maskPan_16digits() {
    assertEquals("************1111", MaskingConverter.mask("4111111111111111"));
  }

  @Test
  void maskPan_14digits() {
    assertEquals("**********1111", MaskingConverter.mask("41111111111111"));
  }

  @Test
  void maskPan_19digits() {
    assertEquals("***************1111", MaskingConverter.mask("4111111111111111111"));
  }

  @Test
  void maskPan_13digits_notMasked() {
    assertEquals("4111111111111", MaskingConverter.mask("4111111111111"));
  }

  @Test
  void maskPan_20digits_notMasked() {
    assertEquals("41111111111111111111", MaskingConverter.mask("41111111111111111111"));
  }

  @Test
  void maskPan_uuid_notMasked() {
    String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    assertEquals(uuid, MaskingConverter.mask(uuid));
  }

  @Test
  void maskCvv_colonSpace() {
    assertEquals("cvv: ***", MaskingConverter.mask("cvv: 123"));
  }

  @Test
  void maskCvv_singleQuoted() {
    assertEquals("cvv='***'", MaskingConverter.mask("cvv='456'"));
  }

  @Test
  void maskCvv_4digit() {
    assertEquals("CVV=***", MaskingConverter.mask("CVV=7890"));
  }

  @Test
  void maskCvv_jsonFormat() {
    assertEquals("\"cvv\":\"***\"", MaskingConverter.mask("\"cvv\":\"123\""));
  }

  @Test
  void mask_mixedMessage() {
    assertEquals(
        "Card ************8877 cvv: ***",
        MaskingConverter.mask("Card 2222405343248877 cvv: 123"));
  }

  @Test
  void mask_nonSensitive() {
    assertEquals("Payment processed", MaskingConverter.mask("Payment processed"));
  }

  @Test
  void mask_null() {
    assertNull(MaskingConverter.mask(null));
  }

  @Test
  void mask_generatedProcessPaymentRequestToString() {
    String input = "class ProcessPaymentRequest {\n"
        + "    cardNumber: 2222405343248877\n"
        + "    expiryMonth: 4\n"
        + "    expiryYear: 2027\n"
        + "    currency: GBP\n"
        + "    amount: 100\n"
        + "    cvv: 123\n"
        + "}";
    String masked = MaskingConverter.mask(input);
    assertEquals("class ProcessPaymentRequest {\n"
        + "    cardNumber: ************8877\n"
        + "    expiryMonth: 4\n"
        + "    expiryYear: 2027\n"
        + "    currency: GBP\n"
        + "    amount: 100\n"
        + "    cvv: ***\n"
        + "}", masked);
  }

  @Test
  void mask_generatedBankPaymentRequestToString() {
    String input = "class BankPaymentRequest {\n"
        + "    cardNumber: 2222405343248877\n"
        + "    expiryDate: 04/2025\n"
        + "    currency: GBP\n"
        + "    amount: 100\n"
        + "    cvv: 123\n"
        + "}";
    String masked = MaskingConverter.mask(input);
    assertEquals("class BankPaymentRequest {\n"
        + "    cardNumber: ************8877\n"
        + "    expiryDate: 04/2025\n"
        + "    currency: GBP\n"
        + "    amount: 100\n"
        + "    cvv: ***\n"
        + "}", masked);
  }

  @Test
  void mask_exceptionMessage() {
    String input = "org.springframework.web.client.HttpServerErrorException: "
        + "500 Server Error for card 4111111111111111";
    String masked = MaskingConverter.mask(input);
    assertEquals("org.springframework.web.client.HttpServerErrorException: "
        + "500 Server Error for card ************1111", masked);
  }
}
