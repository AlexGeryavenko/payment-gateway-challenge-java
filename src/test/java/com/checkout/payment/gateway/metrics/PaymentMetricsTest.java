package com.checkout.payment.gateway.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentMetricsTest {

  private SimpleMeterRegistry registry;
  private PaymentMetrics metrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    metrics = new PaymentMetrics(registry);
  }

  @Test
  void recordPaymentProcessed_incrementsCounter() {
    metrics.recordPaymentProcessed("AUTHORIZED", "GBP");

    Counter counter = registry.find("payment.processed")
        .tag("status", "AUTHORIZED")
        .tag("currency", "GBP")
        .counter();
    assertNotNull(counter);
    assertEquals(1.0, counter.count());
  }

  @Test
  void recordPaymentAmount_recordsSummary() {
    metrics.recordPaymentAmount("USD", 5000);

    DistributionSummary summary = registry.find("payment.amount")
        .tag("currency", "USD")
        .summary();
    assertNotNull(summary);
    assertEquals(1, summary.count());
    assertEquals(5000.0, summary.totalAmount());
  }

  @Test
  void recordPaymentRetrieved_incrementsCounter() {
    metrics.recordPaymentRetrieved(true);
    metrics.recordPaymentRetrieved(false);

    Counter foundCounter = registry.find("payment.retrieved")
        .tag("found", "true")
        .counter();
    Counter notFoundCounter = registry.find("payment.retrieved")
        .tag("found", "false")
        .counter();
    assertNotNull(foundCounter);
    assertNotNull(notFoundCounter);
    assertEquals(1.0, foundCounter.count());
    assertEquals(1.0, notFoundCounter.count());
  }

  @Test
  void recordBankCallDuration_recordsTimer() throws Exception {
    String result = metrics.recordBankCallDuration(() -> "OK");

    assertEquals("OK", result);
    Timer timer = registry.find("bank.authorization.duration").timer();
    assertNotNull(timer);
    assertEquals(1, timer.count());
  }
}
