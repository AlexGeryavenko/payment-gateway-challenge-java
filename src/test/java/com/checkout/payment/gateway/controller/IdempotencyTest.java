package com.checkout.payment.gateway.controller;

import static com.checkout.payment.gateway.controller.JsonFixture.readFixture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.bank.api.DefaultApi;
import com.checkout.payment.gateway.client.bank.model.BankPaymentResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyTest {

  @Autowired
  private MockMvc mvc;

  @MockBean
  private DefaultApi bankApi;

  @Test
  void postPayment_withIdempotencyKey_secondCallReturnsCachedResponse() throws Exception {
    String idempotencyKey = UUID.randomUUID().toString();
    when(bankApi.authorizePayment(any())).thenReturn(
        new BankPaymentResponse().authorized(true).authorizationCode("auth-idem"));

    MvcResult firstResult = mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", idempotencyKey)
            .content(readFixture("/fixtures/valid-payment.json")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andReturn();

    String firstId = com.jayway.jsonpath.JsonPath
        .read(firstResult.getResponse().getContentAsString(), "$.id");

    MvcResult secondResult = mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", idempotencyKey)
            .content(readFixture("/fixtures/valid-payment.json")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andReturn();

    String secondId = com.jayway.jsonpath.JsonPath
        .read(secondResult.getResponse().getContentAsString(), "$.id");

    org.junit.jupiter.api.Assertions.assertEquals(firstId, secondId);
    verify(bankApi, times(1)).authorizePayment(any());
  }

  @Test
  void postPayment_withoutIdempotencyKey_processesNormally() throws Exception {
    when(bankApi.authorizePayment(any())).thenReturn(
        new BankPaymentResponse().authorized(true).authorizationCode("auth-no-idem"));

    mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(readFixture("/fixtures/valid-payment.json")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.id").exists());
  }

  @Test
  void postPayment_differentIdempotencyKeys_processesBothSeparately() throws Exception {
    when(bankApi.authorizePayment(any())).thenReturn(
        new BankPaymentResponse().authorized(true).authorizationCode("auth-diff"));

    MvcResult firstResult = mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .content(readFixture("/fixtures/valid-payment.json")))
        .andExpect(status().isCreated())
        .andReturn();

    MvcResult secondResult = mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .content(readFixture("/fixtures/valid-payment.json")))
        .andExpect(status().isCreated())
        .andReturn();

    String firstId = com.jayway.jsonpath.JsonPath
        .read(firstResult.getResponse().getContentAsString(), "$.id");
    String secondId = com.jayway.jsonpath.JsonPath
        .read(secondResult.getResponse().getContentAsString(), "$.id");

    org.junit.jupiter.api.Assertions.assertNotEquals(firstId, secondId);
    verify(bankApi, times(2)).authorizePayment(any());
  }
}
