package com.checkout.payment.gateway.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentStatus;
import com.checkout.payment.gateway.repository.PaymentRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerCharacterizationTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private PaymentRepository paymentRepository;

  @Test
  void getPayment_existingId_returnsAllFieldsIncludingId() throws Exception {
    Payment payment = new Payment();
    payment.setId(UUID.randomUUID());
    payment.setAmount(500);
    payment.setCurrency("GBP");
    payment.setStatus(PaymentStatus.DECLINED);
    payment.setExpiryMonth(6);
    payment.setExpiryYear(2025);
    payment.setCardNumberLastFour("9876");

    paymentRepository.save(payment);

    mvc.perform(MockMvcRequestBuilders.get("/v1/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(payment.getId().toString()))
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("9876"))
        .andExpect(jsonPath("$.expiryMonth").value(6))
        .andExpect(jsonPath("$.expiryYear").value(2025))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(500));
  }

  @Test
  void getPayment_existingId_returnsJsonContentType() throws Exception {
    Payment payment = new Payment();
    payment.setId(UUID.randomUUID());
    payment.setAmount(500);
    payment.setCurrency("GBP");
    payment.setStatus(PaymentStatus.DECLINED);
    payment.setExpiryMonth(6);
    payment.setExpiryYear(2025);
    payment.setCardNumberLastFour("9876");

    paymentRepository.save(payment);

    mvc.perform(MockMvcRequestBuilders.get("/v1/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
  }

  @Test
  void getPayment_nonExistentId_returns404() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/v1/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"));
  }
}
