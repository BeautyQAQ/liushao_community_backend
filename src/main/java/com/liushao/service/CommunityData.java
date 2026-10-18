package com.liushao.service;

import com.liushao.common.exception.ApiException;
import com.liushao.model.vo.PageResult;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

/** Shared bound-parameter queries for social and operator APIs. */
@Component
public class CommunityData {
  public static final String PUBLIC_POST = "p.moderation_status='PUBLISHED' AND u.status=TRUE";
  public static final String PUBLIC_COMMENT =
      "c.visible=TRUE AND u.status=TRUE AND (c.parent_id IS NULL OR (parent.visible=TRUE AND"
          + " pu.status=TRUE))";
  public static final String COMMENT_FROM =
      " FROM bms_comment c JOIN ums_user u ON u.id=c.user_id LEFT JOIN bms_comment parent ON"
          + " parent.id=c.parent_id LEFT JOIN ums_user pu ON pu.id=parent.user_id";
  public final JdbcTemplate db;

  public CommunityData(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> rows(String sql, Object... args) {
    return db.query(
        sql,
        (rs, row) -> {
          Map<String, Object> result = new LinkedHashMap<>();
          var metadata = rs.getMetaData();
          for (int i = 1; i <= metadata.getColumnCount(); i++) {
            String label = metadata.getColumnLabel(i).toLowerCase(Locale.ROOT);
            String[] pieces = label.split("_");
            StringBuilder key = new StringBuilder(pieces[0]);
            for (int j = 1; j < pieces.length; j++)
              key.append(Character.toUpperCase(pieces[j].charAt(0))).append(pieces[j].substring(1));
            Object value = rs.getObject(i);
            if (value instanceof Timestamp timestamp) value = timestamp.toInstant().toString();
            if (value instanceof Number number
                && Set.of("is_read", "visible", "enabled", "active", "top", "essence", "status")
                    .contains(label)) value = number.intValue() != 0;
            result.put(label.equals("is_read") ? "read" : key.toString(), value);
          }
          return result;
        },
        args);
  }

  public Map<String, Object> one(String sql, Object... args) {
    var result = rows(sql, args);
    if (result.isEmpty()) throw new ApiException(404, "记录不存在");
    return result.getFirst();
  }

  public long count(String sql, Object... args) {
    return Objects.requireNonNull(db.queryForObject(sql, Long.class, args));
  }

  public long insert(String sql, Object... args) {
    var key = new GeneratedKeyHolder();
    db.update(
        connection -> {
          var statement = connection.prepareStatement(sql, new String[] {"id"});
          for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
          return statement;
        },
        key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }

  public PageResult<Map<String, Object>> page(
      String columns, String from, String order, List<Object> args, int pageNo, int size) {
    bounds(pageNo, size);
    long total = count("SELECT COUNT(*)" + from, args.toArray());
    var queryArgs = new ArrayList<>(args);
    queryArgs.add(size);
    queryArgs.add((long) (pageNo - 1) * size);
    var content =
        rows(
            "SELECT " + columns + from + " ORDER BY " + order + " LIMIT ? OFFSET ?",
            queryArgs.toArray());
    return new PageResult<>(content, total, (int) ((total + size - 1) / size), pageNo - 1, size);
  }

  public static void bounds(int pageNo, int size) {
    if (pageNo < 1 || size < 1 || size > 50 || (long) (pageNo - 1) * size > Integer.MAX_VALUE)
      throw new ApiException(400, "分页参数不正确");
  }

  public static String search(String query) {
    if (query == null || query.length() > 100) throw new ApiException(400, "搜索内容最多100个字符");
    return "%"
        + query
            .trim()
            .toLowerCase(Locale.ROOT)
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_")
        + "%";
  }

  public Map<String, Object> user(String username) {
    if (username == null || !username.matches("[a-zA-Z0-9_]{2,32}"))
      throw new ApiException(404, "用户不存在");
    return one(
        "SELECT id,username,alias,avatar,bio FROM ums_user WHERE username=? AND status=TRUE",
        username.toLowerCase(Locale.ROOT));
  }

  public Map<String, Object> publicPost(int postId, boolean lock) {
    return one(
        "SELECT p.id,p.user_id,p.title FROM bms_post p JOIN ums_user u ON u.id=p.user_id WHERE"
            + " p.id=? AND "
            + PUBLIC_POST
            + (lock ? " FOR UPDATE" : ""),
        postId);
  }

  public void lockPost(int postId) {
    one("SELECT id FROM bms_post WHERE id=? FOR UPDATE", postId);
  }

  public void refreshPostCounts(int postId) {
    long comments =
        count(
            "SELECT COUNT(*)" + COMMENT_FROM + " WHERE c.post_id=? AND " + PUBLIC_COMMENT, postId);
    long likes =
        count(
            "SELECT COUNT(*) FROM bms_post_like l JOIN ums_user u ON u.id=l.user_id WHERE"
                + " l.post_id=? AND u.status=TRUE",
            postId);
    long bookmarks =
        count(
            "SELECT COUNT(*) FROM bms_bookmark b JOIN ums_user u ON u.id=b.user_id WHERE"
                + " b.post_id=? AND u.status=TRUE",
            postId);
    db.update(
        "UPDATE bms_post SET comments=?,likes=?,collects=? WHERE id=?",
        comments,
        likes,
        bookmarks,
        postId);
  }

  public void notify(
      int recipient, Integer actor, String type, Integer postId, Long commentId, String content) {
    if (actor != null && recipient == actor) return;
    db.update(
        "INSERT INTO ums_notification(recipient_id,actor_id,type,post_id,comment_id,content)"
            + " VALUES(?,?,?,?,?,?)",
        recipient,
        actor,
        type,
        postId,
        commentId,
        notificationPreview(content));
  }

  private static String notificationPreview(String content) {
    int end = Math.min(255, content.length());
    // H2 measures VARCHAR in UTF-16 units; keep the same portable bound on MySQL
    // without cutting between an emoji's high and low surrogate.
    if (end < content.length()
        && Character.isHighSurrogate(content.charAt(end - 1))
        && Character.isLowSurrogate(content.charAt(end))) end--;
    return content.substring(0, end);
  }

  public static int id(Map<String, Object> row, String field) {
    return ((Number) row.get(field)).intValue();
  }

  public static long longId(Map<String, Object> row, String field) {
    return ((Number) row.get(field)).longValue();
  }
}
