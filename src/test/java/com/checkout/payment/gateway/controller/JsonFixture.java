package com.checkout.payment.gateway.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class JsonFixture {

  private JsonFixture() {}

  public static String readFixture(String path) {
    try (InputStream in = JsonFixture.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalArgumentException("Fixture not found: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read fixture: " + path, e);
    }
  }
}