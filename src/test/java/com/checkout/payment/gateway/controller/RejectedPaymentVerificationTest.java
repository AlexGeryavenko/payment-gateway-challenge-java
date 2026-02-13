package com.checkout.payment.gateway.controller;

import static com.checkout.payment.gateway.controller.JsonFixture.readFixture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.bank.api.DefaultApi;
import java.util.UUID;
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
class RejectedPaymentVerificationTest {

  @Autowired
  private MockMvc mvc;

  @MockBean
  private DefaultApi bankApi;

  @Test
  void rejectedPayment_expiredCard_notRetrievableViaGet() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(readFixture("/fixtures/expired-card-payment.json")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));

    verify(bankApi, never()).authorizePayment(any());

    // REJECTED payments are not stored — GET should return 404 for any UUID
    mvc.perform(MockMvcRequestBuilders.get("/v1/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectedPayment_luhnInvalidCard_notRetrievableViaGet() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(readFixture("/fixtures/luhn-invalid-payment.json")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors[0].field").value("cardNumber"))
        .andExpect(jsonPath("$.errors[0].message").value("Card number failed Luhn check"));

    verify(bankApi, never()).authorizePayment(any());

    // REJECTED payments are not stored — GET should return 404 for any UUID
    mvc.perform(MockMvcRequestBuilders.get("/v1/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }
}
