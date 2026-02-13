package com.checkout.payment.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.bank.api.DefaultApi;
import com.checkout.payment.gateway.client.bank.model.BankPaymentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestClientException;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentProcessingTest {

  @Autowired
  private MockMvc mvc;

  @MockBean
  private DefaultApi bankApi;

  @Test
  void postPayment_authorizedCard_returns200Authorized() throws Exception {
    when(bankApi.authorizePayment(any())).thenReturn(
        new BankPaymentResponse().authorized(true).authorizationCode("auth-123"));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.cardNumberLastFour").value("8877"))
        .andExpect(jsonPath("$.expiryMonth").value(4))
        .andExpect(jsonPath("$.expiryYear").value(2027))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100));
  }

  @Test
  void postPayment_declinedCard_returns200Declined() throws Exception {
    when(bankApi.authorizePayment(any())).thenReturn(
        new BankPaymentResponse().authorized(false).authorizationCode(""));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.id").exists());
  }

  @Test
  void postPayment_expiredCard_returns400Rejected() throws Exception {
    String expiredCardJson = """
        {
          "cardNumber": "2222405343248877",
          "expiryMonth": 1,
          "expiryYear": 2024,
          "currency": "GBP",
          "amount": 100,
          "cvv": "123"
        }
        """;

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(expiredCardJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));

    verify(bankApi, never()).authorizePayment(any());
  }

  @Test
  void postPayment_thenGetPayment_returnsSameData() throws Exception {
    when(bankApi.authorizePayment(any())).thenReturn(
        new BankPaymentResponse().authorized(true).authorizationCode("auth-456"));

    MvcResult postResult = mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentJson()))
        .andExpect(status().isOk())
        .andReturn();

    String responseBody = postResult.getResponse().getContentAsString();
    String id = com.jayway.jsonpath.JsonPath.read(responseBody, "$.id");

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("8877"))
        .andExpect(jsonPath("$.expiryMonth").value(4))
        .andExpect(jsonPath("$.expiryYear").value(2027))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100));
  }

  @Test
  void postPayment_bankError_returns502() throws Exception {
    when(bankApi.authorizePayment(any())).thenThrow(new RestClientException("Service unavailable"));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentJson()))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value("Bank service unavailable"));
  }

  @Test
  void postPayment_invalidCardNumber_returns400Rejected() throws Exception {
    String invalidCardJson = """
        {
          "cardNumber": "123",
          "expiryMonth": 4,
          "expiryYear": 2027,
          "currency": "GBP",
          "amount": 100,
          "cvv": "123"
        }
        """;

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidCardJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));

    verify(bankApi, never()).authorizePayment(any());
  }

  @Test
  void postPayment_invalidCurrency_returns400Rejected() throws Exception {
    String invalidCurrencyJson = """
        {
          "cardNumber": "2222405343248877",
          "expiryMonth": 4,
          "expiryYear": 2027,
          "currency": "JPY",
          "amount": 100,
          "cvv": "123"
        }
        """;

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidCurrencyJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));
  }

  private String validPaymentJson() {
    return """
        {
          "cardNumber": "2222405343248877",
          "expiryMonth": 4,
          "expiryYear": 2027,
          "currency": "GBP",
          "amount": 100,
          "cvv": "123"
        }
        """;
  }
}
