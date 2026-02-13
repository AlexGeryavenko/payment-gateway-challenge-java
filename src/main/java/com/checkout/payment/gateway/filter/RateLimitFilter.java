package com.checkout.payment.gateway.filter;

import com.checkout.payment.gateway.configuration.RateLimitProperties;
import com.checkout.payment.gateway.configuration.RateLimitProperties.EndpointLimit;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final RateLimitProperties properties;

  public RateLimitFilter(RateLimitProperties properties) {
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/payment");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String key = request.getRemoteAddr() + ":" + request.getMethod();
    EndpointLimit limit = "POST".equals(request.getMethod())
        ? properties.getPost() : properties.getGet();
    Bucket bucket = buckets.computeIfAbsent(key, k -> buildBucket(limit));

    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
      filterChain.doFilter(request, response);
    } else {
      long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000 + 1;
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("{\"message\":\"Rate limit exceeded. Try again later.\"}");
    }
  }

  private Bucket buildBucket(EndpointLimit limit) {
    return Bucket.builder()
        .addLimit(Bandwidth.builder()
            .capacity(limit.getCapacity())
            .refillGreedy(limit.getRefillRate(), Duration.ofSeconds(1))
            .build())
        .build();
  }

  public void clearBuckets() {
    buckets.clear();
  }
}
