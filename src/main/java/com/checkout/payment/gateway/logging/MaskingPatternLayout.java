package com.checkout.payment.gateway.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.PatternLayout;

public class MaskingPatternLayout extends PatternLayout {

  @Override
  public String doLayout(ILoggingEvent event) {
    return MaskingConverter.mask(super.doLayout(event));
  }
}
