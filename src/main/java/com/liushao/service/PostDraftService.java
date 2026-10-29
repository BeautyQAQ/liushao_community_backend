package com.liushao.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liushao.common.exception.ApiException;
import com.liushao.model.dto.CreateTopicDTO;
import com.liushao.model.dto.DraftRequests;
import com.liushao.model.entity.UmsUser;
import com.liushao.model.vo.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostDraftService {
  private final CommunityData data;
  private final ObjectMapper json;
  private final BmsPostService posts;

  public PostDraftService(CommunityData data, ObjectMapper json, BmsPostService posts) {
    this.data = data;
    this.json = json;
    this.posts = posts;
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> list(UmsUser user, int page, int size) {
    return data.page(
        "id,post_id,title,LEFT(content,160) AS excerpt,version,updated_at",
        " FROM bms_draft WHERE user_id=?",
        "updated_at DESC,id DESC",
        List.of(user.getId()),
        page,
        size);
  }

  @Transactional(readOnly = true)
  public PostDraft get(UUID id, UmsUser user) {
    return draft(owned(id, user, false));
  }

  @Transactional
  public PostDraft save(UUID id, UmsUser user, DraftRequests.Save input) {
    // Every draft mutation locks its owner first, including concurrent saves of the same UUID.
    lockOwner(user);
    // Do not lock an absent UUID: MySQL gap locks can deadlock unrelated authors' inserts.
    var existing = data.rows("SELECT * FROM bms_draft WHERE id=?", id.toString());
    if (!existing.isEmpty() && CommunityData.id(existing.getFirst(), "userId") != user.getId())
      throw new ApiException(404, "草稿不存在");
    if ((input.postId() == null) != (input.postVersion() == null))
      throw new ApiException(400, "编辑草稿需要原帖及版本");
    if (input.postId() != null) {
      var post = data.one("SELECT user_id FROM bms_post WHERE id=?", input.postId());
      if (CommunityData.id(post, "userId") != user.getId())
        throw new ApiException(403, "只能为自己的帖子保存编辑草稿");
    }
    String tags = encode(input.tags());
    if (existing.isEmpty()) {
      if (input.version() != 0) throw new ApiException(409, "草稿已删除或已发布，请保留内容后刷新");
      if (data.count("SELECT COUNT(*) FROM bms_draft WHERE user_id=?", user.getId()) >= 100)
        throw new ApiException(400, "草稿箱已满，请先发布或删除部分草稿");
      try {
        data.db.update(
            "INSERT INTO bms_draft(id,user_id,post_id,post_version,title,content,tags)"
                + " VALUES(?,?,?,?,?,?,?)",
            id.toString(), user.getId(), input.postId(), input.postVersion(), input.title(),
            input.content(), tags);
      } catch (org.springframework.dao.DuplicateKeyException collision) {
        // The owner lock serializes this account; only another account can claim this UUID.
        throw new ApiException(404, "草稿不存在");
      }
    } else {
      var current = draft(existing.getFirst());
      checkVersion(current, input.version());
      if (!Objects.equals(current.postId(), input.postId())
          || !Objects.equals(current.postVersion(), input.postVersion()))
        throw new ApiException(409, "草稿的原帖版本已变化，请保留内容后刷新");
      data.db.update(
          "UPDATE bms_draft SET title=?,content=?,tags=?,version=version+1,"
              + " updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",
          input.title(), input.content(), tags, id.toString(), user.getId());
    }
    return get(id, user);
  }

  @Transactional
  public void delete(UUID id, UmsUser user, long version) {
    lockOwner(user);
    checkVersion(draft(owned(id, user, true)), version);
    data.db.update("DELETE FROM bms_draft WHERE id=? AND user_id=?", id.toString(), user.getId());
  }

  @Transactional
  public PostDetail publish(UUID id, UmsUser user, long version) {
    lockOwner(user);
    var draft = draft(owned(id, user, true));
    checkVersion(draft, version);
    if (draft.title().isBlank() || draft.content().isBlank())
      throw new ApiException(400, "请填写标题和正文后再发布");
    var input = new CreateTopicDTO();
    input.setTitle(draft.title());
    input.setContent(draft.content());
    input.setTags(draft.tags());
    input.setVersion(draft.postVersion());
    // The post write, tag changes and draft removal share one transaction.
    var result = draft.postId() == null
        ? posts.add(input, user) : posts.update(draft.postId(), input, user);
    data.db.update("DELETE FROM bms_draft WHERE id=? AND user_id=?", id.toString(), user.getId());
    return result;
  }

  private Map<String, Object> owned(UUID id, UmsUser user, boolean lock) {
    var rows = data.rows(
        "SELECT * FROM bms_draft WHERE id=? AND user_id=?" + (lock ? " FOR UPDATE" : ""),
        id.toString(), user.getId());
    if (rows.isEmpty()) throw new ApiException(404, "草稿不存在或已发布");
    return rows.getFirst();
  }

  private void lockOwner(UmsUser user) {
    // All draft writes acquire the owner before the draft/post, including FK inserts on publish.
    data.one("SELECT id FROM ums_user WHERE id=? AND status=TRUE FOR UPDATE", user.getId());
  }

  private void checkVersion(PostDraft draft, long version) {
    if (draft.version() != version)
      throw new ApiException(409, "草稿已在其他页面更新，请保留当前内容后重新打开");
  }

  private PostDraft draft(Map<String, Object> row) {
    try {
      return new PostDraft(
          (String) row.get("id"),
          row.get("postId") == null ? null : CommunityData.id(row, "postId"),
          row.get("postVersion") == null ? null : CommunityData.longId(row, "postVersion"),
          (String) row.get("title"),
          (String) row.get("content"),
          json.readValue((String) row.get("tags"), new TypeReference<List<String>>() {}),
          CommunityData.longId(row, "version"),
          (String) row.get("updatedAt"));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Invalid stored draft", e);
    }
  }

  private String encode(List<String> tags) {
    try {
      return json.writeValueAsString(tags);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid draft tags", e);
    }
  }
}
