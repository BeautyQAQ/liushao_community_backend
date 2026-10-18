package com.liushao.service;

import static com.liushao.service.CommunityData.*;

import com.liushao.common.exception.ApiException;
import com.liushao.model.vo.PageResult;
import com.liushao.security.CommunitySessions;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunitySocialService {
  private final CommunityData data;
  private final CommunitySessions sessions;
  private static final String COMMENT_COLUMNS =
      "c.id,c.post_id,c.parent_id,u.username,u.alias,u.avatar,c.content,c.created_at";
  private static final String MESSAGE_COLUMNS =
      "m.id,s.username AS sender_username,r.username AS"
          + " recipient_username,m.content,m.created_at,(m.read_at IS NOT NULL) AS is_read";
  private static final String MESSAGE_FROM =
      " FROM ums_message m JOIN ums_user s ON s.id=m.sender_id JOIN ums_user r ON"
          + " r.id=m.recipient_id";
  private static final String NOTIFICATION_FROM =
      " FROM ums_notification n LEFT JOIN ums_user a ON a.id=n.actor_id LEFT JOIN bms_post p ON"
          + " p.id=n.post_id LEFT JOIN ums_user author ON author.id=p.user_id LEFT JOIN bms_comment"
          + " c ON c.id=n.comment_id LEFT JOIN bms_comment parent ON parent.id=c.parent_id LEFT"
          + " JOIN ums_user pu ON pu.id=parent.user_id WHERE n.recipient_id=? AND (n.actor_id IS"
          + " NULL OR a.status=TRUE) AND (n.type='MODERATION' OR n.post_id IS NULL OR"
          + " (p.moderation_status='PUBLISHED' AND author.status=TRUE)) AND (n.comment_id IS NULL"
          + " OR (c.visible=TRUE AND (c.parent_id IS NULL OR (parent.visible=TRUE AND"
          + " pu.status=TRUE))))";

  public CommunitySocialService(CommunityData data, CommunitySessions sessions) {
    this.data = data;
    this.sessions = sessions;
  }

  private int userId(String username) {
    return id(data.user(username), "id");
  }

  private void rate(int id, String action, int limit) {
    if (!sessions.allow(action + ":user:" + id, limit))
      throw new ApiException(429, "操作过于频繁，请一分钟后再试");
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> comments(int postId, int page, int size) {
    data.publicPost(postId, false);
    return data.page(
        COMMENT_COLUMNS,
        COMMENT_FROM + " WHERE c.post_id=? AND " + PUBLIC_COMMENT,
        "c.created_at,c.id",
        List.of(postId),
        page,
        size);
  }

  @Transactional
  public Map<String, Object> comment(int postId, String username, String content, Long parentId) {
    int user = userId(username);
    rate(user, "comment", 30);
    var post = data.publicPost(postId, true);
    Integer replyRecipient = null;
    if (parentId != null) {
      var parent =
          data.one(
              "SELECT c.id,c.parent_id,c.user_id"
                  + COMMENT_FROM
                  + " WHERE c.id=? AND c.post_id=? AND "
                  + PUBLIC_COMMENT,
              parentId,
              postId);
      replyRecipient = id(parent, "userId");
      if (parent.get("parentId") != null) parentId = longId(parent, "parentId");
    }
    long comment =
        data.insert(
            "INSERT INTO bms_comment(post_id,user_id,parent_id,content) VALUES(?,?,?,?)",
            postId,
            user,
            parentId,
            content.trim());
    data.refreshPostCounts(postId);
    data.notify(id(post, "userId"), user, "COMMENT", postId, comment, content.trim());
    if (replyRecipient != null && replyRecipient != id(post, "userId"))
      data.notify(replyRecipient, user, "REPLY", postId, comment, content.trim());
    return data.one("SELECT " + COMMENT_COLUMNS + COMMENT_FROM + " WHERE c.id=?", comment);
  }

  @Transactional
  public void deleteComment(long commentId, String username) {
    int user = userId(username);
    var comment = data.one("SELECT post_id,user_id FROM bms_comment WHERE id=?", commentId);
    if (id(comment, "userId") != user) throw new ApiException(403, "只能删除自己的评论");
    int post = id(comment, "postId");
    data.lockPost(post);
    data.db.update("DELETE FROM bms_comment WHERE id=? AND user_id=?", commentId, user);
    data.refreshPostCounts(post);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> interaction(int postId, String username) {
    int user = userId(username);
    data.publicPost(postId, false);
    var stats =
        data.one("SELECT likes,collects AS bookmarks,comments FROM bms_post WHERE id=?", postId);
    stats.put(
        "liked",
        data.count("SELECT COUNT(*) FROM bms_post_like WHERE post_id=? AND user_id=?", postId, user)
            > 0);
    stats.put(
        "bookmarked",
        data.count("SELECT COUNT(*) FROM bms_bookmark WHERE post_id=? AND user_id=?", postId, user)
            > 0);
    return stats;
  }

  @Transactional
  public Map<String, Object> react(int postId, String username, boolean bookmark, boolean enabled) {
    int user = userId(username);
    rate(user, "relationship", 60);
    var post = data.publicPost(postId, true);
    String table = bookmark ? "bms_bookmark" : "bms_post_like";
    boolean exists =
        data.count("SELECT COUNT(*) FROM " + table + " WHERE post_id=? AND user_id=?", postId, user)
            > 0;
    if (enabled && !exists) {
      data.db.update("INSERT INTO " + table + "(post_id,user_id) VALUES(?,?)", postId, user);
      if (!bookmark) data.notify(id(post, "userId"), user, "LIKE", postId, null, "赞了你的帖子");
    } else if (!enabled && exists) {
      data.db.update("DELETE FROM " + table + " WHERE post_id=? AND user_id=?", postId, user);
      if (!bookmark)
        data.db.update(
            "DELETE FROM ums_notification WHERE actor_id=? AND post_id=? AND type='LIKE'",
            user,
            postId);
    }
    data.refreshPostCounts(postId);
    return interaction(postId, username);
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> bookmarks(String username, int page, int size) {
    int user = userId(username);
    return data.page(
        "p.id,p.title,u.username,u.alias,u.avatar,p.create_time,p.likes,p.comments,p.collects",
        " FROM bms_bookmark b JOIN bms_post p ON p.id=b.post_id JOIN ums_user u ON u.id=p.user_id"
            + " WHERE b.user_id=? AND "
            + PUBLIC_POST,
        "b.created_at DESC,p.id DESC",
        List.of(user),
        page,
        size);
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> ownPosts(String username, int page, int size) {
    int user = userId(username);
    return data.page(
        "p.id,p.title,p.moderation_status AS"
            + " status,p.moderation_reason,p.create_time,p.likes,p.comments,p.collects",
        " FROM bms_post p WHERE p.user_id=?",
        "p.create_time DESC,p.id DESC",
        List.of(user),
        page,
        size);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> relationship(String targetName, String username) {
    int user = userId(username), target = userId(targetName);
    return Map.of(
        "following",
            data.count(
                    "SELECT COUNT(*) FROM ums_follow WHERE follower_id=? AND followed_id=?",
                    user,
                    target)
                > 0,
        "followers",
            data.count(
                "SELECT COUNT(*) FROM ums_follow f JOIN ums_user u ON u.id=f.follower_id WHERE"
                    + " f.followed_id=? AND u.status=TRUE",
                target),
        "followingCount",
            data.count(
                "SELECT COUNT(*) FROM ums_follow f JOIN ums_user u ON u.id=f.followed_id WHERE"
                    + " f.follower_id=? AND u.status=TRUE",
                target));
  }

  @Transactional
  public Map<String, Object> follow(String targetName, String username, boolean enabled) {
    int user = userId(username), target = userId(targetName);
    if (user == target) throw new ApiException(400, "不能关注自己");
    rate(user, "relationship", 60);
    data.rows("SELECT id FROM ums_user WHERE id IN (?,?) ORDER BY id FOR UPDATE", user, target);
    boolean exists =
        data.count(
                "SELECT COUNT(*) FROM ums_follow WHERE follower_id=? AND followed_id=?",
                user,
                target)
            > 0;
    if (enabled && !exists) {
      data.db.update("INSERT INTO ums_follow(follower_id,followed_id) VALUES(?,?)", user, target);
      data.notify(target, user, "FOLLOW", null, null, "关注了你");
    } else if (!enabled && exists) {
      data.db.update("DELETE FROM ums_follow WHERE follower_id=? AND followed_id=?", user, target);
      data.db.update(
          "DELETE FROM ums_notification WHERE actor_id=? AND recipient_id=? AND type='FOLLOW'",
          user,
          target);
    }
    return relationship(targetName, username);
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> follows(
      String username, boolean followers, int page, int size) {
    int user = userId(username);
    String joined = followers ? "follower_id" : "followed_id",
        owned = followers ? "followed_id" : "follower_id";
    return data.page(
        "u.id,u.username,u.alias,u.avatar,u.bio",
        " FROM ums_follow f JOIN ums_user u ON u.id=f."
            + joined
            + " WHERE f."
            + owned
            + "=? AND u.status=TRUE",
        "f.created_at DESC,u.id DESC",
        List.of(user),
        page,
        size);
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> notifications(String username, int page, int size) {
    return data.page(
        "n.id,n.type,a.username AS actor_username,a.alias AS"
            + " actor_alias,n.post_id,n.comment_id,n.content,n.created_at,(n.read_at IS NOT NULL)"
            + " AS is_read",
        NOTIFICATION_FROM,
        "n.id DESC",
        List.of(userId(username)),
        page,
        size);
  }

  @Transactional(readOnly = true)
  public Map<String, Long> unreadNotifications(String username) {
    return Map.of(
        "count",
        data.count(
            "SELECT COUNT(*)" + NOTIFICATION_FROM + " AND n.read_at IS NULL", userId(username)));
  }

  @Transactional
  public void readNotification(String username, Long id) {
    int user = userId(username);
    if (id == null)
      data.db.update(
          "UPDATE ums_notification SET read_at=CURRENT_TIMESTAMP WHERE recipient_id=? AND read_at"
              + " IS NULL",
          user);
    else {
      data.one("SELECT id FROM ums_notification WHERE id=? AND recipient_id=?", id, user);
      data.db.update(
          "UPDATE ums_notification SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP) WHERE id=? AND"
              + " recipient_id=?",
          id,
          user);
    }
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> messages(
      String username, String otherName, int page, int size) {
    int user = userId(username), other = userId(otherName);
    return data.page(
        MESSAGE_COLUMNS,
        MESSAGE_FROM
            + " WHERE (m.sender_id=? AND m.recipient_id=?) OR (m.sender_id=? AND m.recipient_id=?)",
        "m.id DESC",
        List.of(user, other, other, user),
        page,
        size);
  }

  @Transactional
  public Map<String, Object> sendMessage(String username, String otherName, String content) {
    int user = userId(username), other = userId(otherName);
    if (user == other) throw new ApiException(400, "不能给自己发送私信");
    rate(user, "message", 20);
    long id =
        data.insert(
            "INSERT INTO ums_message(sender_id,recipient_id,content) VALUES(?,?,?)",
            user,
            other,
            content.trim());
    return data.one("SELECT " + MESSAGE_COLUMNS + MESSAGE_FROM + " WHERE m.id=?", id);
  }

  @Transactional
  public void readMessages(String username, String otherName) {
    int user = userId(username), other = userId(otherName);
    data.db.update(
        "UPDATE ums_message SET read_at=CURRENT_TIMESTAMP WHERE recipient_id=? AND sender_id=? AND"
            + " read_at IS NULL",
        user,
        other);
  }

  @Transactional(readOnly = true)
  public Map<String, Long> unreadMessages(String username) {
    return Map.of(
        "count",
        data.count(
            "SELECT COUNT(*) FROM ums_message m JOIN ums_user s ON s.id=m.sender_id WHERE"
                + " m.recipient_id=? AND m.read_at IS NULL AND s.status=TRUE",
            userId(username)));
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> conversations(String username, int page, int size) {
    int user = userId(username);
    String from =
        " FROM ums_message m JOIN ums_user u ON u.id=CASE WHEN m.sender_id=? THEN m.recipient_id"
            + " ELSE m.sender_id END WHERE u.status=TRUE AND m.id IN (SELECT MAX(id) FROM"
            + " ums_message WHERE sender_id=? OR recipient_id=? GROUP BY"
            + " LEAST(sender_id,recipient_id),GREATEST(sender_id,recipient_id))";
    var result =
        data.page(
            "u.id,u.username,u.alias,u.avatar,m.content AS last_content,m.created_at",
            from,
            "m.id DESC",
            List.of(user, user, user),
            page,
            size);
    // At most 50 conversations; one grouped inbox query supplies all unread counts.
    var unread =
        data.rows(
            "SELECT sender_id,COUNT(*) AS unread FROM ums_message WHERE recipient_id=? AND read_at"
                + " IS NULL GROUP BY sender_id",
            user);
    Map<Integer, Long> counts = new HashMap<>();
    unread.forEach(row -> counts.put(id(row, "senderId"), longId(row, "unread")));
    result.content().forEach(row -> row.put("unread", counts.getOrDefault(id(row, "id"), 0L)));
    return result;
  }
}
