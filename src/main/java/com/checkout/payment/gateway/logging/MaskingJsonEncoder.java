package com.checkout.payment.gateway.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.nio.charset.StandardCharsets;
import net.logstash.logback.encoder.LogstashEncoder;

public class MaskingJsonEncoder extends LogstashEncoder {

  @Override
  public byte[] encode(ILoggingEvent event) {
    byte[] original = super.encode(event);
    String json = new String(original, StandardCharsets.UTF_8);
    return MaskingConverter.mask(json).getBytes(StandardCharsets.UTF_8);
  }
}
