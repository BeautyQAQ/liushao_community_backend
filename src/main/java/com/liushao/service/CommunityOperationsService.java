package com.liushao.service;

import static com.liushao.service.CommunityData.*;

import com.liushao.common.exception.ApiException;
import com.liushao.model.vo.PageResult;
import java.net.URI;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityOperationsService {
  private final CommunityData data;
  private static final String USER_COLUMNS =
      "id,username,alias,email,avatar,status,active,bio,create_time";
  private static final String POST_COLUMNS =
      "p.id,p.title,u.username,p.moderation_status AS"
          + " status,p.top,p.essence,p.comments,p.collects,p.likes,p.create_time,p.modify_time";
  private static final String POST_FROM = " FROM bms_post p JOIN ums_user u ON u.id=p.user_id";
  private static final String COMMENT_COLUMNS =
      "c.id,c.post_id,p.title AS"
          + " post_title,c.parent_id,u.username,c.content,c.visible,c.created_at";
  private static final String COMMENT_FROM =
      " FROM bms_comment c JOIN bms_post p ON p.id=c.post_id JOIN ums_user u ON u.id=c.user_id";

  public CommunityOperationsService(CommunityData data) {
    this.data = data;
  }

  public void audit(String actor, String action, String resource, long resourceId, String detail) {
    data.db.update(
        "INSERT INTO community_audit(actor,action,resource,resource_id,detail) VALUES(?,?,?,?,?)",
        actor,
        action,
        resource,
        resourceId,
        detail);
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> users(String query, int page, int size) {
    String pattern = search(query);
    return data.page(
        USER_COLUMNS,
        " FROM ums_user WHERE LOWER(username) LIKE ? ESCAPE '!' OR LOWER(alias) LIKE ? ESCAPE '!'"
            + " OR LOWER(email) LIKE ? ESCAPE '!'",
        "id DESC",
        List.of(pattern, pattern, pattern),
        page,
        size);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> user(int id) {
    return data.one("SELECT " + USER_COLUMNS + " FROM ums_user WHERE id=?", id);
  }

  @Transactional
  public Map<String, Object> userStatus(int id, boolean status, String reason, String actor) {
    data.one("SELECT id FROM ums_user WHERE id=? FOR UPDATE", id);
    data.db.update(
        "UPDATE ums_user SET status=?,auth_version=auth_version+1,modify_time=CURRENT_TIMESTAMP"
            + " WHERE id=?",
        status,
        id);
    // Disabled actors stop contributing visible counters; restoring an account restores its
    // contributions.
    var affected =
        data.rows(
            "SELECT post_id FROM bms_comment WHERE user_id=? UNION SELECT post_id FROM"
                + " bms_post_like WHERE user_id=? UNION SELECT post_id FROM bms_bookmark WHERE"
                + " user_id=? ORDER BY post_id",
            id,
            id,
            id);
    affected.forEach(
        row -> {
          int post = id(row, "postId");
          data.lockPost(post);
          data.refreshPostCounts(post);
        });
    audit(actor, status ? "user.enable" : "user.disable", "user", id, reason);
    return user(id);
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> posts(String query, String status, int page, int size) {
    String from =
        POST_FROM
            + " WHERE (LOWER(p.title) LIKE ? ESCAPE '!' OR LOWER(u.username) LIKE ? ESCAPE '!')";
    List<Object> args = new ArrayList<>(List.of(search(query), search(query)));
    if (status != null && !status.isBlank()) {
      checkStatus(status);
      from += " AND p.moderation_status=?";
      args.add(status);
    }
    return data.page(POST_COLUMNS, from, "p.id DESC", args, page, size);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> post(int id) {
    var result =
        data.one(
            "SELECT "
                + POST_COLUMNS
                + ",p.content,p.version,p.moderation_reason"
                + POST_FROM
                + " WHERE p.id=?",
            id);
    result.put(
        "tags",
        data.rows(
            "SELECT t.id,t.name,t.topic_count FROM bms_tag t JOIN bms_post_tag pt ON pt.tag_id=t.id"
                + " WHERE pt.topic_id=? ORDER BY t.id",
            id));
    return result;
  }

  @Transactional
  public Map<String, Object> moderate(
      int id, String status, boolean top, boolean essence, String reason, String actor) {
    checkStatus(status);
    data.lockPost(id);
    var old = data.one("SELECT user_id FROM bms_post WHERE id=?", id);
    data.db.update(
        "UPDATE bms_post SET"
            + " moderation_status=?,moderation_reason=?,top=?,essence=?,version=version+1,modify_time=CURRENT_TIMESTAMP"
            + " WHERE id=?",
        status,
        reason,
        top,
        essence,
        id);
    data.notify(
        id(old, "userId"), null, "MODERATION", id, null, "帖子状态已更新：" + status + "。" + reason);
    audit(
        actor,
        "post.moderate",
        "post",
        id,
        "status=" + status + ", top=" + top + ", essence=" + essence + "; " + reason);
    return post(id);
  }

  @Transactional
  public void deletePost(int id, String reason, String actor) {
    data.lockPost(id);
    var tags = data.rows("SELECT tag_id FROM bms_post_tag WHERE topic_id=? ORDER BY tag_id", id);
    tags.forEach(
        row ->
            data.db.update(
                "UPDATE bms_tag SET topic_count=topic_count-1 WHERE id=?", id(row, "tagId")));
    data.db.update("DELETE FROM bms_post WHERE id=?", id);
    audit(actor, "post.delete", "post", id, reason);
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> comments(
      String query, Integer postId, int page, int size) {
    String from =
        COMMENT_FROM
            + " WHERE (LOWER(c.content) LIKE ? ESCAPE '!' OR LOWER(u.username) LIKE ? ESCAPE '!')";
    List<Object> args = new ArrayList<>(List.of(search(query), search(query)));
    if (postId != null) {
      from += " AND c.post_id=?";
      args.add(postId);
    }
    return data.page(COMMENT_COLUMNS, from, "c.id DESC", args, page, size);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> comment(long id) {
    return data.one("SELECT " + COMMENT_COLUMNS + COMMENT_FROM + " WHERE c.id=?", id);
  }

  @Transactional
  public Map<String, Object> commentVisibility(
      long id, boolean visible, String reason, String actor) {
    var comment = comment(id);
    int post = id(comment, "postId");
    data.lockPost(post);
    data.db.update("UPDATE bms_comment SET visible=? WHERE id=?", visible, id);
    data.refreshPostCounts(post);
    audit(actor, visible ? "comment.show" : "comment.hide", "comment", id, reason);
    return comment(id);
  }

  @Transactional
  public void deleteComment(long id, String reason, String actor) {
    var comment = comment(id);
    int post = id(comment, "postId");
    data.lockPost(post);
    data.db.update("DELETE FROM bms_comment WHERE id=?", id);
    data.refreshPostCounts(post);
    audit(actor, "comment.delete", "comment", id, reason);
  }

  private record Catalog(String table, String columns, String searchColumn) {}

  private static Catalog catalog(String kind) {
    return switch (kind) {
      case "billboards" ->
          new Catalog("bms_billboard", "id,content,is_visible AS visible,create_time", "content");
      case "tips" -> new Catalog("bms_tip", "id,content,author,type AS enabled", "content");
      case "promotions" -> new Catalog("bms_promotion", "id,title,link,description", "title");
      case "tags" -> new Catalog("bms_tag", "id,name,topic_count", "name");
      default -> throw new ApiException(404, "资源不存在");
    };
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> catalogList(
      String kind, String query, int page, int size) {
    var c = catalog(kind);
    return data.page(
        c.columns(),
        " FROM " + c.table() + " WHERE LOWER(" + c.searchColumn() + ") LIKE ? ESCAPE '!'",
        "id DESC",
        List.of(search(query)),
        page,
        size);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> catalogItem(String kind, long id) {
    var c = catalog(kind);
    return data.one("SELECT " + c.columns() + " FROM " + c.table() + " WHERE id=?", id);
  }

  @Transactional
  public Map<String, Object> saveCatalog(
      String kind, Long id, Map<String, Object> input, String actor) {
    var c = catalog(kind);
    if (id != null) data.one("SELECT id FROM " + c.table() + " WHERE id=? FOR UPDATE", id);
    String fields, assignments;
    List<Object> values = new ArrayList<>();
    switch (kind) {
      case "billboards" -> {
        fields = "content,is_visible,create_time";
        assignments = "content=?,is_visible=?";
        values.add(text(input, "content", 255, true));
        values.add(bool(input, "visible"));
      }
      case "tips" -> {
        fields = "content,author,type";
        assignments = "content=?,author=?,type=?";
        values.add(text(input, "content", 255, true));
        values.add(text(input, "author", 255, false));
        values.add(bool(input, "enabled"));
      }
      case "promotions" -> {
        fields = "title,link,description";
        assignments = "title=?,link=?,description=?";
        String link = text(input, "link", 255, true);
        try {
          URI uri = URI.create(link);
          if (!Set.of("http", "https")
                  .contains(Objects.toString(uri.getScheme(), "").toLowerCase(Locale.ROOT))
              || uri.getHost() == null
              || uri.getUserInfo() != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
          throw new ApiException(400, "链接必须是有效的HTTP或HTTPS地址");
        }
        values.add(text(input, "title", 255, true));
        values.add(link);
        values.add(text(input, "description", 255, false));
      }
      case "tags" -> {
        fields = "name";
        assignments = "name=?";
        values.add(text(input, "name", 32, true).toLowerCase(Locale.ROOT));
      }
      default -> throw new ApiException(404, "资源不存在");
    }
    boolean created = id == null;
    if (created) {
      String placeholders = String.join(",", Collections.nCopies(values.size(), "?"));
      if (kind.equals("billboards")) placeholders += ",CURRENT_TIMESTAMP";
      id =
          data.insert(
              "INSERT INTO " + c.table() + "(" + fields + ") VALUES(" + placeholders + ")",
              values.toArray());
    } else {
      values.add(id);
      data.db.update(
          "UPDATE " + c.table() + " SET " + assignments + " WHERE id=?", values.toArray());
    }
    audit(actor, kind + (created ? ".create" : ".update"), kind, id, created ? "创建内容" : "更新内容");
    return catalogItem(kind, id);
  }

  @Transactional
  public void deleteCatalog(String kind, long id, String actor) {
    var c = catalog(kind);
    data.one("SELECT id FROM " + c.table() + " WHERE id=? FOR UPDATE", id);
    if (kind.equals("tags")
        && data.count("SELECT COUNT(*) FROM bms_post_tag WHERE tag_id=?", id) > 0)
      throw new ApiException(409, "标签仍有关联帖子，不能删除");
    data.db.update("DELETE FROM " + c.table() + " WHERE id=?", id);
    audit(actor, kind + ".delete", kind, id, "删除内容");
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> auditList(String query, int page, int size) {
    String pattern = search(query);
    return data.page(
        "id,actor,action,resource,resource_id,detail,created_at",
        " FROM community_audit WHERE LOWER(actor) LIKE ? ESCAPE '!' OR LOWER(action) LIKE ? ESCAPE"
            + " '!' OR LOWER(resource) LIKE ? ESCAPE '!'",
        "id DESC",
        List.of(pattern, pattern, pattern),
        page,
        size);
  }

  public static String text(Map<String, Object> input, String key, int max, boolean required) {
    Object value = input.get(key);
    if (value == null && !required) return "";
    if (!(value instanceof String s) || s.length() > max || (required && s.isBlank()))
      throw new ApiException(400, "字段" + key + "格式不正确");
    return ((String) value).trim();
  }

  public static boolean bool(Map<String, Object> input, String key) {
    if (!(input.get(key) instanceof Boolean b)) throw new ApiException(400, "字段" + key + "必须为布尔值");
    return b;
  }

  private static void checkStatus(String status) {
    if (!Set.of("PUBLISHED", "PENDING", "HIDDEN").contains(status))
      throw new ApiException(400, "帖子状态不正确");
  }
}
