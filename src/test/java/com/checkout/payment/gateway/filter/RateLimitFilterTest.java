package com.checkout.payment.gateway.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.configuration.RateLimitProperties;
import com.checkout.payment.gateway.configuration.RateLimitProperties.EndpointLimit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

  private RateLimitFilter filter;

  @BeforeEach
  void setUp() {
    RateLimitProperties properties = new RateLimitProperties();
    properties.setPost(new EndpointLimit(2, 1));
    properties.setGet(new EndpointLimit(2, 1));
    filter = new RateLimitFilter(properties);
  }

  @Test
  void allowsRequestsWithinCapacity() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payment");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, new MockFilterChain());

    assertEquals(200, response.getStatus());
  }

  @Test
  void rejectsRequestsWhenCapacityExhausted() throws Exception {
    for (int i = 0; i < 2; i++) {
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payment");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilterInternal(request, response, new MockFilterChain());
      assertEquals(200, response.getStatus());
    }

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payment");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilterInternal(request, response, new MockFilterChain());

    assertEquals(429, response.getStatus());
  }

  @Test
  void rejectedResponseIncludesRetryAfterHeader() throws Exception {
    for (int i = 0; i < 2; i++) {
      filter.doFilterInternal(
          new MockHttpServletRequest("POST", "/v1/payment"),
          new MockHttpServletResponse(), new MockFilterChain());
    }

    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilterInternal(
        new MockHttpServletRequest("POST", "/v1/payment"), response, new MockFilterChain());

    assertEquals(429, response.getStatus());
    assertNotNull(response.getHeader("Retry-After"));
    assertTrue(Long.parseLong(response.getHeader("Retry-After")) > 0);
  }

  @Test
  void rejectedResponseIncludesJsonBody() throws Exception {
    for (int i = 0; i < 2; i++) {
      filter.doFilterInternal(
          new MockHttpServletRequest("POST", "/v1/payment"),
          new MockHttpServletResponse(), new MockFilterChain());
    }

    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilterInternal(
        new MockHttpServletRequest("POST", "/v1/payment"), response, new MockFilterChain());

    assertEquals("application/json", response.getContentType());
    assertTrue(response.getContentAsString().contains("Rate limit exceeded"));
  }

  @Test
  void successfulRequestIncludesRemainingHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payment");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, new MockFilterChain());

    assertNotNull(response.getHeader("X-Rate-Limit-Remaining"));
    assertEquals("1", response.getHeader("X-Rate-Limit-Remaining"));
  }

  @Test
  void shouldNotFilterNonPaymentPaths() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    assertTrue(filter.shouldNotFilter(request));
  }

  @Test
  void shouldFilterPaymentPaths() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payment/123");
    assertTrue(!filter.shouldNotFilter(request));
  }

  @Test
  void separateBucketsForDifferentMethods() throws Exception {
    for (int i = 0; i < 2; i++) {
      filter.doFilterInternal(
          new MockHttpServletRequest("POST", "/v1/payment"),
          new MockHttpServletResponse(), new MockFilterChain());
    }

    MockHttpServletResponse postResponse = new MockHttpServletResponse();
    filter.doFilterInternal(
        new MockHttpServletRequest("POST", "/v1/payment"), postResponse, new MockFilterChain());
    assertEquals(429, postResponse.getStatus());

    MockHttpServletResponse getResponse = new MockHttpServletResponse();
    filter.doFilterInternal(
        new MockHttpServletRequest("GET", "/v1/payment/123"), getResponse, new MockFilterChain());
    assertEquals(200, getResponse.getStatus());
  }
}
