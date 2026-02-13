package com.checkout.payment.gateway.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bank-simulator")
public class BankSimulatorProperties {

  private String baseUrl = "http://localhost:8080";

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }
}
