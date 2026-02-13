package com.checkout.payment.gateway.client;

import static com.checkout.payment.gateway.controller.JsonFixture.readFixture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.bank.api.DefaultApi;
import com.checkout.payment.gateway.client.bank.model.BankPaymentResponse;
import com.checkout.payment.gateway.filter.RateLimitFilter;
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
    "resilience4j.retry.instances.bankClient.max-attempts=3",
    "resilience4j.retry.instances.bankClient.wait-duration=10ms",
    "resilience4j.circuitbreaker.instances.bankClient.sliding-window-size=100",
    "resilience4j.circuitbreaker.instances.bankClient.failure-rate-threshold=100",
    "rate-limit.post.capacity=1000",
    "rate-limit.post.refill-rate=1000"
})
class RetryTest {

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
  void retries3TimesBeforeReturning502() throws Exception {
    when(bankApi.authorizePayment(any()))
        .thenThrow(new RestClientException("Connection refused"));

    mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(readFixture("/fixtures/valid-payment.json")))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value("Bank service unavailable"));

    verify(bankApi, times(3)).authorizePayment(any());
  }

  @Test
  void succeedsOnSecondAttemptAfterTransientFailure() throws Exception {
    when(bankApi.authorizePayment(any()))
        .thenThrow(new RestClientException("Connection refused"))
        .thenReturn(new BankPaymentResponse().authorized(true).authorizationCode("retry-ok"));

    mvc.perform(MockMvcRequestBuilders.post("/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(readFixture("/fixtures/valid-payment.json")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"));

    verify(bankApi, times(2)).authorizePayment(any());
  }
}
