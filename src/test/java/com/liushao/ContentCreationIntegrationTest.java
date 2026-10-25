package com.liushao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.liushao.security.CommunitySessions;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContentCreationIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entities;
  @MockitoBean CommunitySessions sessions;
  final Map<String, String> active = new HashMap<>();

  @BeforeEach
  void setup() {
    when(sessions.allow(anyString(), anyInt())).thenReturn(true);
    doAnswer(a -> { active.put(a.getArgument(0), a.getArgument(1)); return null; })
        .when(sessions).save(anyString(), anyString(), any(Duration.class));
    when(sessions.active(anyString(), anyString()))
        .thenAnswer(a -> Objects.equals(active.get(a.getArgument(0)), a.getArgument(1)));
  }

  ResultActions call(MockHttpServletRequestBuilder request, Object input, String token) throws Exception {
    entities.flush(); entities.clear();
    if (input != null) request.contentType("application/json").content(json.writeValueAsBytes(input));
    if (token != null) request.header("Authorization", "Bearer " + token);
    return mvc.perform(request);
  }

  JsonNode ok(ResultActions result) throws Exception {
    return json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
  }

  record Account(String name, int id, String token) {}

  Account account() throws Exception {
    String name = "create_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    int id = ok(call(post("/ums/user/register"), Map.of("name", name, "email", name + "@example.com", "pass", "Creation-pass-123", "checkPass", "Creation-pass-123"), null)).path("id").asInt();
    String token = ok(call(post("/ums/user/login"), Map.of("username", name, "password", "Creation-pass-123"), null)).path("token").asText();
    return new Account(name, id, token);
  }

  JsonNode topic(Account author, String title) throws Exception {
    return ok(call(post("/post/add"), Map.of("title", title, "content", "中文正文 🌊", "tags", List.of("discovery")), author.token()));
  }

  Map<String, Object> input(String title, String content, long version) {
    var input = new LinkedHashMap<String, Object>();
    input.put("title", title); input.put("content", content); input.put("tags", List.of("草稿"));
    input.put("postId", null); input.put("postVersion", null); input.put("version", version);
    return input;
  }

  String path() { return "/me/drafts/" + UUID.randomUUID(); }

  @Test
  void essenceUsesExistingModerationFlagsAndPublicVisibilityWithStablePagination() throws Exception {
    var author = account();
    int plain = topic(author, "普通内容").path("id").asInt();
    int pinned = topic(author, "置顶精华").path("id").asInt();
    int second = topic(author, "普通精华").path("id").asInt();
    int hidden = topic(author, "隐藏精华").path("id").asInt();
    var disabled = account();
    int disabledPost = topic(disabled, "停用作者精华").path("id").asInt();
    db.update("UPDATE bms_post SET essence=TRUE WHERE id IN (?,?,?,?)", pinned, second, hidden, disabledPost);
    db.update("UPDATE bms_post SET top=TRUE WHERE id=?", pinned);
    db.update("UPDATE bms_post SET moderation_status='HIDDEN' WHERE id=?", hidden);
    db.update("UPDATE ums_user SET status=FALSE WHERE id=?", disabled.id());
    var firstPage = ok(call(get("/post/list?tab=essence&size=1"), null, null));
    assertThat(firstPage.path("totalElements").asInt()).isEqualTo(2);
    assertThat(firstPage.path("content").get(0).path("id").asInt()).isEqualTo(pinned);
    assertThat(firstPage.path("content").get(0).path("essence").asBoolean()).isTrue();
    assertThat(ok(call(get("/post/list?tab=essence&size=1&pageNo=2"), null, null)).path("content").get(0).path("id").asInt()).isEqualTo(second);
    assertThat(ok(call(get("/post/list?tab=essence&query=普通&tag=discovery"), null, null)).path("totalElements").asInt()).isEqualTo(1);
    assertThat(ok(call(get("/post/list?tab=latest&author=" + author.name()), null, null)).path("totalElements").asInt()).isEqualTo(3);
    assertThat(plain).isPositive();
  }

  @Test
  void followingIsPrivateUsesRealCurrentAccountAndHidesUnpublishedOrDisabledAuthors() throws Exception {
    var reader = account(); var author = account(); var stranger = account();
    int old = topic(author, "旧置顶").path("id").asInt();
    int recent = topic(author, "新主题").path("id").asInt();
    int hidden = topic(author, "待审主题").path("id").asInt();
    topic(stranger, "未关注作者的主题");
    db.update("UPDATE bms_post SET top=TRUE,create_time='2020-01-01 00:00:00' WHERE id=?", old);
    db.update("UPDATE bms_post SET moderation_status='PENDING' WHERE id=?", hidden);
    ok(call(post("/users/" + author.name() + "/follow"), null, reader.token()));
    call(get("/me/feed").header("userName", reader.name()), null, null).andExpect(status().isUnauthorized());
    var feed = ok(call(get("/me/feed?size=1"), null, reader.token()));
    assertThat(feed.path("totalElements").asInt()).isEqualTo(2);
    assertThat(feed.path("content").get(0).path("id").asInt()).isEqualTo(recent);
    assertThat(ok(call(get("/me/feed?size=1&pageNo=2"), null, reader.token())).path("content").get(0).path("id").asInt()).isEqualTo(old);
    assertThat(ok(call(get("/me/feed").header("userName", reader.name()), null, stranger.token())).path("totalElements").asInt()).isZero();
    assertThat(ok(call(get("/me/feed?query=不存在"), null, reader.token())).path("totalElements").asInt()).isZero();
    call(get("/post/list?tab=following"), null, reader.token()).andExpect(status().isBadRequest());
    db.update("UPDATE ums_user SET status=FALSE WHERE id=?", author.id());
    assertThat(ok(call(get("/me/feed"), null, reader.token())).path("totalElements").asInt()).isZero();
    db.update("UPDATE ums_user SET status=TRUE WHERE id=?", author.id());
    ok(call(delete("/users/" + author.name() + "/follow"), null, reader.token()));
    assertThat(ok(call(get("/me/feed"), null, reader.token())).path("totalElements").asInt()).isZero();
  }

  @Test
  void partialDraftsArePrivateAndNeverAppearAsPublicPostsOrTags() throws Exception {
    var author = account(); var stranger = account(); String path = path();
    var saved = ok(call(put(path), input("", "只写了一半\n🌊", 0), author.token()));
    assertThat(saved.path("version").asLong()).isEqualTo(1);
    assertThat(saved.path("content").asText()).isEqualTo("只写了一半\n🌊");
    assertThat(ok(call(get("/me/drafts"), null, author.token())).path("totalElements").asInt()).isEqualTo(1);
    assertThat(ok(call(get("/me/drafts"), null, stranger.token())).path("totalElements").asInt()).isZero();
    for (String route : List.of("/me/drafts", path)) call(get(route), null, null).andExpect(status().isUnauthorized());
    call(get(path), null, stranger.token()).andExpect(status().isNotFound());
    call(put(path), input("抢占草稿", "不允许", 0), stranger.token()).andExpect(status().isNotFound());
    call(delete(path), Map.of("version", 1), stranger.token()).andExpect(status().isNotFound());
    call(post(path + "/publish"), Map.of("version", 1), stranger.token()).andExpect(status().isNotFound());
    assertThat(db.queryForObject("SELECT COUNT(*) FROM bms_post WHERE user_id=?", Integer.class, author.id())).isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM bms_tag WHERE name='草稿'", Integer.class)).isZero();
  }

  @Test
  void staleSavesAndDeletesCannotOverwriteOrResurrectDrafts() throws Exception {
    var author = account(); String path = path();
    ok(call(put(path), input("初稿", "第一版", 0), author.token()));
    ok(call(put(path), input("已更新", "第二版", 1), author.token()));
    call(put(path), input("旧页面", "不能覆盖", 1), author.token()).andExpect(status().isConflict());
    call(delete(path), Map.of("version", 1), author.token()).andExpect(status().isConflict());
    assertThat(ok(call(get(path), null, author.token())).path("content").asText()).isEqualTo("第二版");
    ok(call(delete(path), Map.of("version", 2), author.token()));
    call(put(path), input("旧页面", "不能复活", 2), author.token()).andExpect(status().isConflict());
    call(get(path), null, author.token()).andExpect(status().isNotFound());
  }

  @Test
  void publishingRequiresCompleteContentAndCreatesExactlyOnePostThenRemovesDraft() throws Exception {
    var author = account(); String path = path();
    ok(call(put(path), input("", "未完成", 0), author.token()));
    call(post(path + "/publish"), Map.of("version", 1), author.token()).andExpect(status().isBadRequest());
    assertThat(ok(call(get(path), null, author.token())).path("version").asInt()).isEqualTo(1);
    ok(call(put(path), input("正式标题", "## Markdown\n\n中文 🌊", 1), author.token()));
    var published = ok(call(post(path + "/publish"), Map.of("version", 2), author.token()));
    assertThat(published.path("title").asText()).isEqualTo("正式标题");
    assertThat(published.path("tags").get(0).path("name").asText()).isEqualTo("草稿");
    call(get(path), null, author.token()).andExpect(status().isNotFound());
    call(post(path + "/publish"), Map.of("version", 2), author.token()).andExpect(status().isNotFound());
    assertThat(db.queryForObject("SELECT COUNT(*) FROM bms_post WHERE user_id=?", Integer.class, author.id())).isEqualTo(1);
    assertThat(ok(call(get("/me/drafts"), null, author.token())).path("totalElements").asInt()).isZero();
  }

  @Test
  void editingDraftsCheckBothOwnershipAndOriginalPostVersionAndPreserveConflicts() throws Exception {
    var author = account(); var stranger = account(); var original = topic(author, "原帖");
    String path = path(); var edit = input("草稿中的修改", "草稿正文", 0);
    edit.put("postId", original.path("id").asInt()); edit.put("postVersion", original.path("version").asLong());
    call(put(path), edit, stranger.token()).andExpect(status().isForbidden());
    ok(call(put(path), edit, author.token()));
    ok(call(put("/post/" + original.path("id").asInt()), Map.of("title", "其他页面已修改", "content", "最新正文", "tags", List.of(), "version", original.path("version").asLong()), author.token()));
    call(post(path + "/publish"), Map.of("version", 1), author.token()).andExpect(status().isConflict());
    assertThat(ok(call(get(path), null, author.token())).path("content").asText()).isEqualTo("草稿正文");
    assertThat(ok(call(get("/post/" + original.path("id").asInt()), null, null)).path("title").asText()).isEqualTo("其他页面已修改");
  }

  @Test
  void publishingAnEditPreservesModerationWorkflowAndDoesNotCreateAnotherPost() throws Exception {
    var author = account(); var original = topic(author, "已隐藏的原帖"); int id = original.path("id").asInt();
    db.update("UPDATE bms_post SET moderation_status='HIDDEN',version=version+1 WHERE id=?", id);
    String path = path(); var edit = input("重新提交", "修改后审核", 0);
    edit.put("postId", id); edit.put("postVersion", original.path("version").asLong() + 1);
    ok(call(put(path), edit, author.token()));
    var result = ok(call(post(path + "/publish"), Map.of("version", 1), author.token()));
    assertThat(result.path("id").asInt()).isEqualTo(id);
    assertThat(result.path("status").asText()).isEqualTo("PENDING");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM bms_post WHERE user_id=?", Integer.class, author.id())).isEqualTo(1);
    call(get("/post/" + id), null, null).andExpect(status().isNotFound());
  }

  @Test
  void draftBoundsAndQuotaAreEnforcedWithoutPreventingExistingDraftUpdates() throws Exception {
    var author = account(); String first = path();
    var invalid = input("a".repeat(161), "正文", 0);
    call(put(first), invalid, author.token()).andExpect(status().isBadRequest());
    invalid = input("标题", "正文", -1);
    call(put(first), invalid, author.token()).andExpect(status().isBadRequest());
    invalid = input("标题", "正文", 0); invalid.put("postVersion", 1);
    call(put(first), invalid, author.token()).andExpect(status().isBadRequest());
    call(get("/me/drafts/not-a-uuid"), null, author.token()).andExpect(status().isBadRequest());
    call(get("/me/drafts?pageNo=0"), null, author.token()).andExpect(status().isBadRequest());
    ok(call(put(first), input("第一份", "正文", 0), author.token()));
    for (int i = 1; i < 100; i++) db.update("INSERT INTO bms_draft(id,user_id,title,content,tags) VALUES(?,?,'','','[]')", UUID.randomUUID().toString(), author.id());
    call(put(path()), input("超额", "正文", 0), author.token()).andExpect(status().isBadRequest());
    ok(call(put(first), input("仍可修改", "正文", 1), author.token()));
    assertThat(ok(call(get("/me/drafts?size=10"), null, author.token())).path("totalElements").asInt()).isEqualTo(100);
  }
}
