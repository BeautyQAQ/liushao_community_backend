package com.liushao;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.TimeZone;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/** Real Connector/J regression: uses connection-local temporary tables, never application rows. */
@EnabledIfEnvironmentVariable(named = "MYSQL_TIME_ZONE_TEST_URL", matches = "jdbc:mysql:.*")
class MysqlTimeZoneIntegrationTest {
  @ParameterizedTest
  @CsvSource({"UTC,UTC", "UTC,Asia/Shanghai", "Asia/Shanghai,UTC", "Asia/Shanghai,Asia/Shanghai"})
  void databaseAndJavaTimestampsPreserveTheSameInstant(String connectionZone, String jvmZone)
      throws Exception {
    var original = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone(jvmZone));
    try {
      var yaml = new YamlPropertiesFactoryBean();
      yaml.setResources(new ClassPathResource("application.yml"));
      var config = new HikariConfig();
      String base = System.getenv("MYSQL_TIME_ZONE_TEST_URL");
      // The legacy URL intentionally omits forceConnectionTimeZoneToSession: Hikari must protect
      // it.
      String zone =
          connectionZone.equals("UTC")
              ? "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
              : "serverTimezone=Asia/Shanghai";
      config.setJdbcUrl(base + (base.contains("?") ? "&" : "?") + zone);
      config.setUsername(System.getenv("MYSQL_TIME_ZONE_TEST_USERNAME"));
      config.setPassword(System.getenv("MYSQL_TIME_ZONE_TEST_PASSWORD"));
      config.addDataSourceProperty(
          "forceConnectionTimeZoneToSession",
          yaml.getObject()
              .getProperty(
                  "spring.datasource.hikari.data-source-properties.forceConnectionTimeZoneToSession"));
      config.setMaximumPoolSize(1);
      try (var pool = new HikariDataSource(config);
          var connection = pool.getConnection()) {
        var before = Instant.now();
        try (var sql = connection.createStatement()) {
          sql.execute(
              "CREATE TEMPORARY TABLE liushao_timezone_regression (from_database TIMESTAMP(6) NOT"
                  + " NULL DEFAULT CURRENT_TIMESTAMP(6), from_java TIMESTAMP(6) NOT NULL)");
        }
        try (var insert =
            connection.prepareStatement(
                "INSERT INTO liushao_timezone_regression(from_java) VALUES(?)")) {
          insert.setTimestamp(1, Timestamp.from(before));
          insert.executeUpdate();
        }
        try (var sql = connection.createStatement();
            var rows =
                sql.executeQuery(
                    "SELECT from_database,from_java,UNIX_TIMESTAMP(from_database) AS"
                        + " database_epoch,UNIX_TIMESTAMP(from_java) AS java_epoch FROM"
                        + " liushao_timezone_regression")) {
          assertThat(rows.next()).isTrue();
          var after = Instant.now();
          assertThat(rows.getTimestamp("from_database").toInstant())
              .isBetween(before.minusSeconds(1), after.plusSeconds(1));
          assertThat(rows.getTimestamp("from_java").toInstant())
              .isBetween(before.minusMillis(1), before.plusMillis(1));
          // Epoch checks also catch a wrong write that is masked by an equally wrong read.
          assertThat(rows.getBigDecimal("database_epoch").longValue())
              .isBetween(before.getEpochSecond() - 1, after.getEpochSecond() + 1);
          assertThat(rows.getBigDecimal("java_epoch").longValue())
              .isEqualTo(before.getEpochSecond());
        }
      }
    } finally {
      TimeZone.setDefault(original);
    }
  }
}
