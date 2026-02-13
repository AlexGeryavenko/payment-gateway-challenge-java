package com.checkout.payment.gateway.filter;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.bank.api.DefaultApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "rate-limit.get.capacity=3",
    "rate-limit.get.refill-rate=1",
    "rate-limit.post.capacity=3",
    "rate-limit.post.refill-rate=1"
})
class RateLimitIntegrationTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private RateLimitFilter rateLimitFilter;

  @MockBean
  private DefaultApi bankApi;

  @AfterEach
  void tearDown() {
    rateLimitFilter.clearBuckets();
  }

  @Test
  void returnsRateLimitRemainingHeader() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payment/00000000-0000-0000-0000-000000000000"))
        .andExpect(header().exists("X-Rate-Limit-Remaining"));
  }

  @Test
  void returns429WhenRateLimitExceeded() throws Exception {
    for (int i = 0; i < 3; i++) {
      mvc.perform(MockMvcRequestBuilders.get("/payment/00000000-0000-0000-0000-000000000000"));
    }

    mvc.perform(MockMvcRequestBuilders.get("/payment/00000000-0000-0000-0000-000000000000"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.message").value("Rate limit exceeded. Try again later."));
  }

  @Test
  void doesNotRateLimitActuatorEndpoints() throws Exception {
    for (int i = 0; i < 5; i++) {
      mvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
          .andExpect(status().isOk());
    }
  }
}
