package com.checkout.payment.gateway.controller;

import static com.checkout.payment.gateway.controller.JsonFixture.readFixture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.bank.api.DefaultApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentValidationEdgeCaseTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired
  private MockMvc mvc;

  @MockBean
  private DefaultApi bankApi;

  // --- Card number ---

  @Test
  void postPayment_cardNumberTooShort13Digits_returns400Rejected() throws Exception {
    performPostAndExpectRejected(validPaymentWith("cardNumber", "1234567890123"));
  }

  @Test
  void postPayment_cardNumberTooLong20Digits_returns400Rejected() throws Exception {
    performPostAndExpectRejected(validPaymentWith("cardNumber", "12345678901234567890"));
  }

  @Test
  void postPayment_cardNumberWithLetters_returns400Rejected() throws Exception {
    performPostAndExpectRejected(validPaymentWith("cardNumber", "2222ABCD43248877"));
  }

  // --- CVV ---

  @Test
  void postPayment_cvvTooShort2Digits_returns400Rejected() throws Exception {
    performPostAndExpectRejected(validPaymentWith("cvv", "12"));
  }

  @Test
  void postPayment_cvvTooLong5Digits_returns400Rejected() throws Exception {
    performPostAndExpectRejected(validPaymentWith("cvv", "12345"));
  }

  @Test
  void postPayment_cvvWithLetters_returns400Rejected() throws Exception {
    performPostAndExpectRejected(validPaymentWith("cvv", "12A"));
  }

  // --- Amount ---

  @Test
  void postPayment_amountZero_returns400Rejected() throws Exception {
    performPostAndExpectRejected(validPaymentWith("amount", 0));
  }

  @Test
  void postPayment_amountNegative_returns400Rejected() throws Exception {
    performPostAndExpectRejected(validPaymentWith("amount", -1));
  }

  // --- Expiry month ---

  @Test
  void postPayment_expiryMonthZero_returns400Rejected() throws Exception {
    performPostAndExpectRejected(validPaymentWith("expiryMonth", 0));
  }

  @Test
  void postPayment_expiryMonth13_returns400Rejected() throws Exception {
    performPostAndExpectRejected(validPaymentWith("expiryMonth", 13));
  }

  // --- Missing required fields ---

  @Test
  void postPayment_missingCardNumber_returns400Rejected() throws Exception {
    performPostAndExpectRejected(validPaymentWithout("cardNumber"));
  }

  @Test
  void postPayment_missingCvv_returns400Rejected() throws Exception {
    performPostAndExpectRejected(validPaymentWithout("cvv"));
  }

  @Test
  void postPayment_missingAmount_returns400Rejected() throws Exception {
    performPostAndExpectRejected(validPaymentWithout("amount"));
  }

  @Test
  void postPayment_missingCurrency_returns400Rejected() throws Exception {
    performPostAndExpectRejected(validPaymentWithout("currency"));
  }

  @Test
  void postPayment_missingExpiryMonth_returns400Rejected() throws Exception {
    performPostAndExpectRejected(validPaymentWithout("expiryMonth"));
  }

  @Test
  void postPayment_missingExpiryYear_returns400Rejected() throws Exception {
    performPostAndExpectRejected(validPaymentWithout("expiryYear"));
  }

  @Test
  void postPayment_emptyBody_returns400Rejected() throws Exception {
    performPostAndExpectRejected("{}");
  }

  // --- Helpers ---

  private void performPostAndExpectRejected(String json) throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));

    verify(bankApi, never()).authorizePayment(any());
  }

  private static ObjectNode validPaymentNode() {
    try {
      return (ObjectNode) MAPPER.readTree(readFixture("/fixtures/valid-payment.json"));
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse valid-payment.json", e);
    }
  }

  private static String validPaymentWith(String field, Object value) {
    ObjectNode node = validPaymentNode();
    if (value instanceof String s) {
      node.put(field, s);
    } else if (value instanceof Integer i) {
      node.put(field, i);
    }
    return node.toString();
  }

  private static String validPaymentWithout(String field) {
    ObjectNode node = validPaymentNode();
    node.remove(field);
    return node.toString();
  }
}
