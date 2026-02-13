package com.checkout.payment.gateway.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MaskingConverter {

  private static final Pattern PAN_PATTERN =
      Pattern.compile("\\b(\\d{10,15})(\\d{4})\\b");

  private static final Pattern CVV_PATTERN =
      Pattern.compile(
          "(cvv[\"']?\\s*[:=]\\s*[\"']?)(\\d{3,4})([\"']?)",
          Pattern.CASE_INSENSITIVE);

  private MaskingConverter() {
  }

  public static String mask(String message) {
    if (message == null) {
      return null;
    }
    return maskCvv(maskPan(message));
  }

  static String maskPan(String input) {
    if (input == null) {
      return null;
    }
    Matcher matcher = PAN_PATTERN.matcher(input);
    StringBuilder sb = new StringBuilder();
    while (matcher.find()) {
      String masked = "*".repeat(matcher.group(1).length()) + matcher.group(2);
      matcher.appendReplacement(sb, Matcher.quoteReplacement(masked));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  static String maskCvv(String input) {
    if (input == null) {
      return null;
    }
    Matcher matcher = CVV_PATTERN.matcher(input);
    StringBuilder sb = new StringBuilder();
    while (matcher.find()) {
      String masked = matcher.group(1) + "***" + matcher.group(3);
      matcher.appendReplacement(sb, Matcher.quoteReplacement(masked));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }
}
