package com.checkout.payment.gateway.configuration;

import com.checkout.payment.gateway.client.bank.ApiClient;
import com.checkout.payment.gateway.client.bank.api.DefaultApi;
import java.time.Clock;
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
  public RestTemplate restTemplate(RestTemplateBuilder builder,
      BankSimulatorProperties properties) {
    return builder
        .setConnectTimeout(properties.getConnectTimeout())
        .setReadTimeout(properties.getReadTimeout())
        .build();
  }

  @Bean
  public ApiClient bankApiClient(RestTemplate restTemplate,
      BankSimulatorProperties properties) {
    ApiClient apiClient = new ApiClient(restTemplate);
    apiClient.setBasePath(properties.getUrl());
    return apiClient;
  }

  @Bean
  public DefaultApi bankDefaultApi(ApiClient bankApiClient) {
    return new DefaultApi(bankApiClient);
  }
}
