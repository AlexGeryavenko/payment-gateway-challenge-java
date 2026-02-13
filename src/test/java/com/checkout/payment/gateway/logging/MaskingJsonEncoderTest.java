package com.checkout.payment.gateway.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class MaskingJsonEncoderTest {

  private MaskingJsonEncoder encoder;
  private LoggerContext context;

  @BeforeEach
  void setUp() {
    context = (LoggerContext) LoggerFactory.getILoggerFactory();
    encoder = new MaskingJsonEncoder();
    encoder.setContext(context);
    encoder.start();
  }

  @Test
  void masksPanInJsonOutput() {
    LoggingEvent event = new LoggingEvent(
        "fqcn", context.getLogger("test"), Level.INFO,
        "Card 2222405343248877 processed", null, null);

    byte[] encoded = encoder.encode(event);
    String json = new String(encoded);

    assertFalse(json.contains("2222405343248877"));
    assertTrue(json.contains("************8877"));
  }

  @Test
  void masksCvvInJsonOutput() {
    LoggingEvent event = new LoggingEvent(
        "fqcn", context.getLogger("test"), Level.INFO,
        "cvv: 123 processed", null, null);

    byte[] encoded = encoder.encode(event);
    String json = new String(encoded);

    assertFalse(json.contains("cvv: 123"));
    assertTrue(json.contains("cvv: ***"));
  }
}
