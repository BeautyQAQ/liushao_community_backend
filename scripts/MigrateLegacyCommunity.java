import java.nio.file.*;
import java.sql.*;
import java.time.ZoneOffset;
import java.util.*;

/** Import the repository's legacy community dump without executing its DDL in the application schema. */
class MigrateLegacyCommunity {
  static final String[] RESOURCES = {
    "ums_user", "bms_tag", "bms_post", "bms_post_tag", "bms_comment",
    "bms_billboard", "bms_tip", "bms_promotion"
  };

  static void execute(Connection connection, String script) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      boolean result = statement.execute(script);
      while (result || statement.getUpdateCount() != -1) {
        if (result) statement.getResultSet().close();
        result = statement.getMoreResults();
      }
    }
  }

  static long count(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
      rows.next();
      return rows.getLong(1);
    }
  }

  static void requireZero(Connection connection, String label, String sql) throws SQLException {
    if (count(connection, sql) != 0) throw new SQLException("Migration validation failed: " + label);
  }

  static String joins(String resource) {
    String users = " JOIN legacy_community_id_map author ON author.resource='ums_user'"
        + " AND author.legacy_id=source.user_id";
    String posts = " JOIN legacy_community_id_map post ON post.resource='bms_post'"
        + " AND post.legacy_id=source.topic_id";
    return switch (resource) {
      case "bms_post" -> users;
      case "bms_comment" -> users + posts;
      case "bms_post_tag" -> posts + " JOIN legacy_community_id_map tag ON tag.resource='bms_tag'"
          + " AND tag.legacy_id=source.tag_id";
      default -> "";
    };
  }

  public static void main(String[] args) throws Exception {
    var config = new Properties();
    try (var reader = Files.newBufferedReader(Path.of(args[0]))) { config.load(reader); }
    String dump = Files.readString(Path.of(args[1]));
    String conversion = Files.readString(Path.of(args[2]));
    String offset = ZoneOffset.of(args[3]).getId();
    if (offset.equals("Z")) offset = "+00:00";
    String url = config.getProperty("DB_URL");
    url += (url.contains("?") ? "&" : "?") + "allowMultiQueries=true&forceConnectionTimeZoneToSession=true";
    String username = config.getProperty("DB_USERNAME"), password = config.getProperty("DB_PASSWORD", "");
    String stage = "liushao_legacy_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    try (Connection target = DriverManager.getConnection(url, username, password)) {
      if (!"liushao_community".equals(target.getCatalog()))
        throw new IllegalArgumentException("Expected the consolidated liushao_community database");
      if (count(target, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"
          + " AND TABLE_NAME='legacy_community_id_map'") != 1)
        throw new IllegalStateException("Start the current community backend first to apply Flyway V5");
      execute(target, "CREATE DATABASE `" + stage + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
      try {
        String sourceUrl = url.replace("/liushao_community?", "/" + stage + "?");
        try (Connection source = DriverManager.getConnection(sourceUrl, username, password)) {
          execute(source, dump);
          for (String table : RESOURCES)
            execute(source, "ALTER TABLE " + table + " CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
          execute(source, "ALTER TABLE bms_follow CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        execute(target, "SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci");
        execute(target, "SET time_zone='+00:00'");
        try (var statement = target.prepareStatement("SET @legacy_source_time_zone=?")) {
          statement.setString(1, offset);
          statement.execute();
        }
        execute(target, "CREATE TEMPORARY TABLE legacy_new_ids (resource VARCHAR(32) NOT NULL,"
            + " legacy_id VARCHAR(64) NOT NULL,current_id BIGINT NOT NULL,PRIMARY KEY(resource,legacy_id))");
        target.setAutoCommit(false);
        try {
          for (String resource : RESOURCES) {
            long maximum = count(target, "SELECT GREATEST((SELECT COALESCE(MAX(id),0) FROM " + resource
                + "),(SELECT COALESCE(MAX(current_id),0) FROM legacy_community_id_map WHERE resource='" + resource + "'))");
            execute(target, "INSERT INTO legacy_new_ids SELECT '" + resource + "',CAST(source.id AS CHAR),"
                + maximum + "+ROW_NUMBER() OVER(ORDER BY source.id) FROM `" + stage + "`." + resource
                + " source" + joins(resource) + " WHERE NOT EXISTS(SELECT 1 FROM legacy_community_id_map existing"
                + " WHERE existing.resource='" + resource + "' AND existing.legacy_id=CAST(source.id AS CHAR))");
            execute(target, "INSERT INTO legacy_community_id_map SELECT resource,legacy_id,current_id"
                + " FROM legacy_new_ids WHERE resource='" + resource + "'");
          }
          execute(target, conversion.replace("${legacy_schema}", "`" + stage + "`"));
          requireZero(target, "post contents and view counts", "SELECT COUNT(*) FROM `" + stage + "`.bms_post source"
              + " JOIN legacy_new_ids ids ON ids.resource='bms_post' AND ids.legacy_id=source.id"
              + " JOIN bms_post current ON current.id=ids.current_id"
              + " WHERE NOT(SHA2(COALESCE(source.content,''),256)<=>SHA2(current.content,256))"
              + " OR NOT(source.title<=>current.title) OR source.view<>current.view");
          requireZero(target, "password hash preservation", "SELECT COUNT(*) FROM `" + stage + "`.ums_user source"
              + " JOIN legacy_new_ids ids ON ids.resource='ums_user' AND ids.legacy_id=source.id"
              + " JOIN ums_user current ON current.id=ids.current_id WHERE NOT(source.password<=>current.password)");
          requireZero(target, "mapped records", "SELECT COUNT(*) FROM legacy_new_ids ids WHERE "
              + String.join(" OR ", Arrays.stream(RESOURCES).map(resource -> "(ids.resource='" + resource
                  + "' AND NOT EXISTS(SELECT 1 FROM " + resource + " actual WHERE actual.id=ids.current_id))").toList()));
          target.commit();
        } catch (Exception error) {
          target.rollback();
          throw error;
        }
        for (String resource : RESOURCES) {
          System.out.println("SOURCE\t" + resource + "\t" + count(target, "SELECT COUNT(*) FROM `" + stage + "`." + resource));
          System.out.println("INSERTED\t" + resource + "\t" + count(target, "SELECT COUNT(*) FROM legacy_new_ids WHERE resource='" + resource + "'"));
          System.out.println("TOTAL\t" + resource + "\t" + count(target, "SELECT COUNT(*) FROM " + resource));
        }
        System.out.println("SOURCE\tbms_follow\t" + count(target, "SELECT COUNT(*) FROM `" + stage + "`.bms_follow"));
        System.out.println("TOTAL\tums_follow\t" + count(target, "SELECT COUNT(*) FROM ums_follow"));
        System.out.println("REJECTED\tbms_post_tag\t" + count(target, "SELECT COUNT(*) FROM `" + stage + "`.bms_post_tag source"
            + " WHERE NOT EXISTS(SELECT 1 FROM legacy_community_id_map p WHERE p.resource='bms_post' AND p.legacy_id=source.topic_id)"
            + " OR NOT EXISTS(SELECT 1 FROM legacy_community_id_map t WHERE t.resource='bms_tag' AND t.legacy_id=source.tag_id)"));
        System.out.println("REJECTED\tbms_follow\t" + count(target, "SELECT COUNT(*) FROM `" + stage + "`.bms_follow source"
            + " WHERE source.parent_id=source.follower_id"
            + " OR NOT EXISTS(SELECT 1 FROM legacy_community_id_map u WHERE u.resource='ums_user' AND u.legacy_id=source.parent_id)"
            + " OR NOT EXISTS(SELECT 1 FROM legacy_community_id_map u WHERE u.resource='ums_user' AND u.legacy_id=source.follower_id)"));
        System.out.println("REPAIRED\tduplicate_or_missing_email\t" + count(target, "SELECT COUNT(*) FROM ums_user u"
            + " JOIN legacy_community_id_map ids ON ids.resource='ums_user' AND ids.current_id=u.id"
            + " WHERE u.email=CONCAT('legacy-',ids.legacy_id,'@example.invalid')"));
        try (var statement = target.createStatement(); var rows = statement.executeQuery(
            "SELECT resource,legacy_id,current_id FROM legacy_community_id_map ORDER BY resource,legacy_id")) {
          while (rows.next()) System.out.println("MAP\t" + rows.getString(1) + "\t" + rows.getString(2) + "\t" + rows.getLong(3));
        }
      } finally {
        target.setAutoCommit(true);
        execute(target, "DROP DATABASE `" + stage + "`");
      }
    }
  }
}
