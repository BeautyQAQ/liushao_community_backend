package com.liushao.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class ProductionEnvironmentValidatorTest {
  private final ProductionEnvironmentValidator validator = new ProductionEnvironmentValidator();

  private MockEnvironment completeEnvironment() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    for (String key : List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD", "REDIS_HOST", "REDIS_PASSWORD", "JWT_SECRET", "CORS_ALLOWED_ORIGINS")) {
      environment.setProperty(key, "test-value-never-print");
    }
    return environment;
  }

  @ParameterizedTest
  @ValueSource(strings = {"DB_URL", "DB_USERNAME", "DB_PASSWORD", "REDIS_HOST", "REDIS_PASSWORD", "JWT_SECRET", "CORS_ALLOWED_ORIGINS"})
  void productionRejectsMissingOrBlankValuesWithoutLeakingCredentials(String key) {
    MockEnvironment environment = completeEnvironment();
    ((org.springframework.core.env.MapPropertySource) environment.getPropertySources().get("mockProperties"))
        .getSource().remove(key);
    assertThatThrownBy(() -> validator.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(key)
        .hasMessageNotContaining("test-value-never-print");
    environment.setProperty(key, "  ");
    assertThatThrownBy(() -> validator.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining(key);
  }

  @Test
  void productionAcceptsExplicitValues() {
    assertThatCode(() -> validator.postProcessEnvironment(completeEnvironment(), new SpringApplication()))
        .doesNotThrowAnyException();
  }

  @Test
  void localAndTestProfilesRetainDevelopmentDefaults() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("test");
    assertThatCode(() -> validator.postProcessEnvironment(environment, new SpringApplication()))
        .doesNotThrowAnyException();
    environment.setActiveProfiles("local");
    assertThatCode(() -> validator.postProcessEnvironment(environment, new SpringApplication()))
        .doesNotThrowAnyException();
  }

  @Test
  void productionDefaultProfileCannotBypassValidation() {
    MockEnvironment environment = new MockEnvironment();
    environment.setDefaultProfiles("prod");
    assertThatThrownBy(() -> validator.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("DB_URL");
  }
}
