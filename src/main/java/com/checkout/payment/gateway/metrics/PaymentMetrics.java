package com.checkout.payment.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

  private final MeterRegistry meterRegistry;

  public PaymentMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordPaymentProcessed(String status, String currency) {
    Counter.builder("payment.processed")
        .tag("status", status)
        .tag("currency", currency)
        .register(meterRegistry)
        .increment();
  }

  public void recordPaymentAmount(String currency, int amount) {
    DistributionSummary.builder("payment.amount")
        .tag("currency", currency)
        .register(meterRegistry)
        .record(amount);
  }

  public void recordPaymentRetrieved(boolean found) {
    Counter.builder("payment.retrieved")
        .tag("found", String.valueOf(found))
        .register(meterRegistry)
        .increment();
  }

  public <T> T recordBankCallDuration(Callable<T> callable) throws Exception {
    Timer timer = Timer.builder("bank.authorization.duration")
        .register(meterRegistry);
    return timer.recordCallable(callable);
  }
}
