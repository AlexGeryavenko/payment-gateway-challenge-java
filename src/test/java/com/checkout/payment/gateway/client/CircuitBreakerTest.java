package com.checkout.payment.gateway.client;

import static com.checkout.payment.gateway.controller.JsonFixture.readFixture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.bank.api.DefaultApi;
import com.checkout.payment.gateway.filter.RateLimitFilter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestClientException;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "resilience4j.circuitbreaker.instances.bankClient.sliding-window-size=4",
    "resilience4j.circuitbreaker.instances.bankClient.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.bankClient.wait-duration-in-open-state=60s",
    "resilience4j.circuitbreaker.instances.bankClient.permitted-number-of-calls-in-half-open-state=1",
    "rate-limit.post.capacity=1000",
    "rate-limit.post.refill-rate=1000"
})
class CircuitBreakerTest {

  @Autowired
  private MockMvc mvc;

  @MockBean
  private DefaultApi bankApi;

  @Autowired
  private CircuitBreakerRegistry circuitBreakerRegistry;

  @Autowired
  private RateLimitFilter rateLimitFilter;

  @AfterEach
  void tearDown() {
    circuitBreakerRegistry.circuitBreaker("bankClient").reset();
    rateLimitFilter.clearBuckets();
  }

  @Test
  void returns502WhenCircuitBreakerIsOpen() throws Exception {
    when(bankApi.authorizePayment(any()))
        .thenThrow(new RestClientException("Connection refused"));

    // Send enough failures to trip the circuit (sliding window = 4, threshold = 50%)
    for (int i = 0; i < 4; i++) {
      mvc.perform(MockMvcRequestBuilders.post("/payment")
              .contentType(MediaType.APPLICATION_JSON)
              .content(validPaymentJson()))
          .andExpect(status().isBadGateway());
    }

    // Circuit should now be open — next request gets CallNotPermittedException
    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentJson()))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value("Bank service unavailable"));
  }

  @Test
  void circuitBreakerResetsAfterManualReset() throws Exception {
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("bankClient");
    cb.transitionToOpenState();

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentJson()))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value("Bank service unavailable"));

    cb.reset();

    when(bankApi.authorizePayment(any())).thenReturn(
        new com.checkout.payment.gateway.client.bank.model.BankPaymentResponse()
            .authorized(true).authorizationCode("auth-reset"));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"));
  }

  private String validPaymentJson() {
    return readFixture("/fixtures/valid-payment.json");
  }
}
