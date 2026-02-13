package com.checkout.payment.gateway.controller;

import static com.checkout.payment.gateway.controller.JsonFixture.readFixture;
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
  void postPayment_authorizedCard_returns201Authorized() throws Exception {
    when(bankApi.authorizePayment(any())).thenReturn(
        new BankPaymentResponse().authorized(true).authorizationCode("auth-123"));

    mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentJson()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.cardNumberLastFour").value("8877"))
        .andExpect(jsonPath("$.expiryMonth").value(4))
        .andExpect(jsonPath("$.expiryYear").value(2027))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100));
  }

  @Test
  void postPayment_declinedCard_returns201Declined() throws Exception {
    when(bankApi.authorizePayment(any())).thenReturn(
        new BankPaymentResponse().authorized(false).authorizationCode(""));

    mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentJson()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.id").exists());
  }

  @Test
  void postPayment_expiredCard_returns400Rejected() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(readFixture("/fixtures/expired-card-payment.json")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Validation failed"))
        .andExpect(jsonPath("$.errors[0].field").value("expiryDate"))
        .andExpect(jsonPath("$.errors[0].message")
            .value("Card expiry date must be in the future"));

    verify(bankApi, never()).authorizePayment(any());
  }

  @Test
  void postPayment_thenGetPayment_returnsSameData() throws Exception {
    when(bankApi.authorizePayment(any())).thenReturn(
        new BankPaymentResponse().authorized(true).authorizationCode("auth-456"));

    MvcResult postResult = mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentJson()))
        .andExpect(status().isCreated())
        .andReturn();

    String responseBody = postResult.getResponse().getContentAsString();
    String id = com.jayway.jsonpath.JsonPath.read(responseBody, "$.id");

    mvc.perform(MockMvcRequestBuilders.get("/v1/payment/" + id))
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

    mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentJson()))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value("Bank service unavailable"));
  }

  @Test
  void postPayment_invalidCardNumber_returns400Rejected() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(readFixture("/fixtures/invalid-card-number-payment.json")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Validation failed"))
        .andExpect(jsonPath("$.errors[0].field").value("cardNumber"));

    verify(bankApi, never()).authorizePayment(any());
  }

  @Test
  void postPayment_invalidCurrency_returns400Rejected() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(readFixture("/fixtures/invalid-currency-payment.json")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Validation failed"))
        .andExpect(jsonPath("$.errors[0].field").value("currency"));
  }

  @Test
  void postPayment_luhnInvalidCard_returns400Rejected() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(readFixture("/fixtures/luhn-invalid-payment.json")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value("Validation failed"))
        .andExpect(jsonPath("$.errors[0].field").value("cardNumber"))
        .andExpect(jsonPath("$.errors[0].message").value("Card number failed Luhn check"));

    verify(bankApi, never()).authorizePayment(any());
  }

  private String validPaymentJson() {
    return readFixture("/fixtures/valid-payment.json");
  }
}
