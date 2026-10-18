package com.liushao.config;

import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/** Fail before creating connections when production credentials are incomplete. */
public final class ProductionEnvironmentValidator implements EnvironmentPostProcessor, Ordered {
  private static final List<String> REQUIRED =
      List.of(
          "DB_URL", "DB_USERNAME", "DB_PASSWORD", "REDIS_HOST", "REDIS_PASSWORD", "JWT_SECRET",
          "CORS_ALLOWED_ORIGINS");

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (!environment.matchesProfiles("prod")) return;
    List<String> missing = REQUIRED.stream()
        .filter(key -> !StringUtils.hasText(environment.getProperty(key)))
        .toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Production configuration requires non-empty values for: " + String.join(", ", missing));
    }
  }

  @Override
  public int getOrder() {
    // Run after ConfigDataEnvironmentPostProcessor has loaded application-prod.yml.
    return Ordered.LOWEST_PRECEDENCE;
  }
}
