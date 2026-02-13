package com.checkout.payment.gateway.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.encoder.LogstashEncoder;

public class MaskingJsonEncoder extends LogstashEncoder {

  @Override
  public byte[] encode(ILoggingEvent event) {
    byte[] original = super.encode(event);
    String json = new String(original);
    return MaskingConverter.mask(json).getBytes();
  }
}
