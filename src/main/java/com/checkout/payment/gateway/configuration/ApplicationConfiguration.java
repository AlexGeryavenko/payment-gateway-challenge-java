package com.checkout.payment.gateway.configuration;

import com.checkout.payment.gateway.client.bank.ApiClient;
import com.checkout.payment.gateway.client.bank.api.DefaultApi;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(BankSimulatorProperties.class)
public class ApplicationConfiguration {

  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        .setConnectTimeout(Duration.ofMillis(10000))
        .setReadTimeout(Duration.ofMillis(10000))
        .build();
  }

  @Bean
  public ApiClient bankApiClient(RestTemplate restTemplate,
      BankSimulatorProperties properties) {
    ApiClient apiClient = new ApiClient(restTemplate);
    apiClient.setBasePath(properties.getBaseUrl());
    return apiClient;
  }

  @Bean
  public DefaultApi bankDefaultApi(ApiClient bankApiClient) {
    return new DefaultApi(bankApiClient);
  }
}
