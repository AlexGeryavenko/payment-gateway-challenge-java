package com.checkout.payment.gateway.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

  private EndpointLimit post = new EndpointLimit(200, 100);
  private EndpointLimit get = new EndpointLimit(1000, 500);

  public EndpointLimit getPost() {
    return post;
  }

  public void setPost(EndpointLimit post) {
    this.post = post;
  }

  public EndpointLimit getGet() {
    return get;
  }

  public void setGet(EndpointLimit get) {
    this.get = get;
  }

  public static class EndpointLimit {

    private long capacity;
    private long refillRate;

    public EndpointLimit() {
    }

    public EndpointLimit(long capacity, long refillRate) {
      this.capacity = capacity;
      this.refillRate = refillRate;
    }

    public long getCapacity() {
      return capacity;
    }

    public void setCapacity(long capacity) {
      this.capacity = capacity;
    }

    public long getRefillRate() {
      return refillRate;
    }

    public void setRefillRate(long refillRate) {
      this.refillRate = refillRate;
    }
  }
}
