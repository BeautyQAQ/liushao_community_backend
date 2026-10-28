package com.liushao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.liushao.security.CommunitySessions;
import com.liushao.utils.JwtUtil;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties =
        "COMMUNITY_ADMIN_KEY=integration-internal-key-0123456789-abcdefghijklmnopqrstuvwxyz")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommunitySocialIntegrationTest {
  static final String KEY = "integration-internal-key-0123456789-abcdefghijklmnopqrstuvwxyz";
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entities;
  @Autowired JwtUtil jwt;
  @MockitoBean CommunitySessions sessions;
  Map<String, String> active = new HashMap<>();

  @BeforeEach
  void setup() {
    when(sessions.allow(anyString(), anyInt())).thenReturn(true);
    doAnswer(
            a -> {
              active.put(a.getArgument(0), a.getArgument(1));
              return null;
            })
        .when(sessions)
        .save(anyString(), anyString(), any(Duration.class));
    when(sessions.active(anyString(), anyString()))
        .thenAnswer(a -> Objects.equals(active.get(a.getArgument(0)), a.getArgument(1)));
    doAnswer(
            a -> {
              active.remove(a.getArgument(0));
              return null;
            })
        .when(sessions)
        .revoke(anyString());
  }

  ResultActions call(MockHttpServletRequestBuilder request, Object input, String token)
      throws Exception {
    // Real HTTP requests have separate persistence contexts (open-in-view=false).
    // This rollback-only test transaction must not cache entities across requests.
    entities.flush();
    entities.clear();
    if (input != null)
      request.contentType("application/json").content(json.writeValueAsBytes(input));
    if (token != null) request.header("Authorization", "Bearer " + token);
    return mvc.perform(request);
  }

  JsonNode ok(ResultActions result) throws Exception {
    return json.readTree(
            result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
        .path("data");
  }

  ResultActions admin(MockHttpServletRequestBuilder request, Object input) throws Exception {
    return call(
        request
            .header("X-Community-Admin-Key", KEY)
            .header("X-Community-Admin-Actor", URLEncoder.encode("运营管理员", StandardCharsets.UTF_8)),
        input,
        null);
  }

  String user(String name) throws Exception {
    ok(
        call(
            post("/ums/user/register"),
            Map.of(
                "name",
                name,
                "email",
                name + "@example.com",
                "pass",
                "Member-pass-123",
                "checkPass",
                "Member-pass-123"),
            null));
    return login(name);
  }

  String login(String name) throws Exception {
    return ok(call(
            post("/ums/user/login"), Map.of("username", name, "password", "Member-pass-123"), null))
        .path("token")
        .asText();
  }

  int userId(String name) {
    return db.queryForObject("SELECT id FROM ums_user WHERE username=?", Integer.class, name);
  }

  JsonNode createPost(String token, String title) throws Exception {
    return ok(
        call(
            post("/post/add"),
            Map.of("title", title, "content", "Markdown 正文 😀", "tags", List.of("social")),
            token));
  }

  long comment(String token, int post, String content, Long parent) throws Exception {
    var input = new LinkedHashMap<String, Object>();
    input.put("content", content);
    if (parent != null) input.put("parentId", parent);
    return ok(call(post("/post/" + post + "/comments"), input, token)).path("id").asLong();
  }

  void moderation(int id, String status) throws Exception {
    ok(
        admin(
            put("/internal/admin/posts/" + id + "/moderation"),
            Map.of("status", status, "top", true, "essence", true, "reason", "内容复核")));
  }

  @Test
  void privateGetRoutesRejectForgedHeadersAndMissingTokens() throws Exception {
    for (String path :
        List.of(
            "/notifications",
            "/notifications/unread",
            "/messages?username=alice",
            "/messages/conversations",
            "/messages/unread",
            "/me/bookmarks",
            "/me/following",
            "/me/followers",
            "/me/posts",
            "/me/posts/1",
            "/post/1/interaction",
            "/post/-1/interaction",
            "/users/alice/relationship"))
      call(
              get(path).header("userName", "alice").header("X-Community-Admin-Actor", "admin"),
              null,
              null)
          .andExpect(status().isUnauthorized());
    call(get("/internal/admin/users"), null, null).andExpect(status().isForbidden());
    call(get("/internal/admin/users").header("X-Community-Admin-Key", "wrong"), null, null)
        .andExpect(status().isForbidden());
    call(get("/internal/admin/users").header("X-Community-Admin-Key", KEY), null, null)
        .andExpect(status().isBadRequest());
    ok(admin(get("/internal/admin/users"), null));
  }

  @Test
  void commentsNormalizeRepliesRejectCrossPostAndDeleteSubtreeWithCounters() throws Exception {
    String alice = user("alice"), bob = user("bob");
    int first = createPost(alice, "first").path("id").asInt(),
        second = createPost(alice, "second").path("id").asInt();
    long root = comment(bob, first, "评论 😀", null),
        reply = comment(alice, first, "回复", root),
        nested = comment(bob, first, "再回复", reply);
    var page = ok(call(get("/post/" + first + "/comments"), null, null));
    assertThat(page.path("totalElements").asInt()).isEqualTo(3);
    assertThat(page.path("content").get(2).path("parentId").asLong()).isEqualTo(root);
    call(post("/post/" + second + "/comments"), Map.of("content", "cross", "parentId", root), bob)
        .andExpect(status().isNotFound());
    call(delete("/comments/" + root), null, alice).andExpect(status().isForbidden());
    ok(call(delete("/comments/" + root), null, bob));
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM bms_comment WHERE id IN (?,?,?)",
                Integer.class,
                root,
                reply,
                nested))
        .isZero();
    assertThat(
            ok(call(get("/post/" + first + "/interaction"), null, alice)).path("comments").asInt())
        .isZero();
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM ums_notification WHERE post_id=?", Integer.class, first))
        .isZero();
  }

  @Test
  void likesBookmarksAndFollowsAreIdempotentAndUnique() throws Exception {
    String alice = user("alice"), bob = user("bob");
    int id = createPost(alice, "social").path("id").asInt();
    for (int n = 0; n < 2; n++) {
      ok(call(post("/post/" + id + "/like"), null, bob));
      ok(call(post("/post/" + id + "/bookmark"), null, bob));
      ok(call(post("/users/alice/follow"), null, bob));
    }
    var stats = ok(call(get("/post/" + id + "/interaction"), null, bob));
    assertThat(stats.path("likes").asInt()).isEqualTo(1);
    assertThat(stats.path("bookmarks").asInt()).isEqualTo(1);
    assertThat(stats.path("liked").asBoolean()).isTrue();
    assertThat(stats.path("bookmarked").asBoolean()).isTrue();
    assertThat(ok(call(get("/me/bookmarks"), null, bob)).path("totalElements").asInt())
        .isEqualTo(1);
    assertThat(ok(call(get("/me/followers"), null, alice)).path("totalElements").asInt())
        .isEqualTo(1);
    assertThat(ok(call(get("/notifications"), null, alice)).path("totalElements").asInt())
        .isEqualTo(2);
    call(post("/users/bob/follow"), null, bob).andExpect(status().isBadRequest());
    for (int n = 0; n < 2; n++) {
      ok(call(delete("/post/" + id + "/like"), null, bob));
      ok(call(delete("/post/" + id + "/bookmark"), null, bob));
      ok(call(delete("/users/alice/follow"), null, bob));
    }
    assertThat(ok(call(get("/post/" + id + "/interaction"), null, bob)).path("likes").asInt())
        .isZero();
    assertThat(ok(call(get("/me/followers"), null, alice)).path("totalElements").asInt()).isZero();
  }

  @Test
  void notificationsAreRecipientScopedAndReadOperationsAreIdempotent() throws Exception {
    String alice = user("alice"), bob = user("bob");
    int id = createPost(alice, "notice").path("id").asInt();
    comment(bob, id, "hello", null);
    ok(call(post("/post/" + id + "/like"), null, bob));
    var notice = ok(call(get("/notifications"), null, alice)).path("content").get(0);
    assertThat(ok(call(get("/notifications/unread"), null, alice)).path("count").asInt())
        .isEqualTo(2);
    assertThat(
            ok(call(get("/notifications").header("userName", "alice"), null, bob))
                .path("totalElements")
                .asInt())
        .isZero();
    call(post("/notifications/" + notice.path("id").asLong() + "/read"), null, bob)
        .andExpect(status().isNotFound());
    ok(call(post("/notifications/" + notice.path("id").asLong() + "/read"), null, alice));
    ok(call(post("/notifications/" + notice.path("id").asLong() + "/read"), null, alice));
    assertThat(ok(call(get("/notifications/unread"), null, alice)).path("count").asInt())
        .isEqualTo(1);
    ok(call(post("/notifications/read-all"), null, alice));
    assertThat(ok(call(get("/notifications/unread"), null, alice)).path("count").asInt()).isZero();
  }

  @Test
  void messagesOnlyExposeParticipantConversationsAndMarkOnlyIncomingMessages() throws Exception {
    String alice = user("alice"), bob = user("bob"), eve = user("eve");
    ok(call(post("/messages"), Map.of("username", "bob", "content", "私信 😀"), alice));
    ok(call(post("/messages"), Map.of("username", "alice", "content", "回复"), bob));
    ok(call(post("/messages"), Map.of("username", "bob", "content", "另外一个会话"), eve));
    assertThat(ok(call(get("/messages?username=bob"), null, alice)).path("totalElements").asInt())
        .isEqualTo(2);
    assertThat(
            ok(call(get("/messages?username=alice").header("userName", "bob"), null, eve))
                .path("totalElements")
                .asInt())
        .isZero();
    var conversations = ok(call(get("/messages/conversations"), null, bob));
    assertThat(conversations.path("totalElements").asInt()).isEqualTo(2);
    assertThat(ok(call(get("/messages/unread"), null, bob)).path("count").asInt()).isEqualTo(2);
    ok(call(post("/messages/read"), Map.of("username", "alice"), bob));
    assertThat(ok(call(get("/messages/unread"), null, bob)).path("count").asInt()).isEqualTo(1);
    assertThat(ok(call(get("/messages/unread"), null, alice)).path("count").asInt()).isEqualTo(1);
    call(post("/messages"), Map.of("username", "alice", "content", "self"), alice)
        .andExpect(status().isBadRequest());
  }

  @Test
  void moderationHidesPostEverywhereAndAuthorEditsRequireReapproval() throws Exception {
    String alice = user("alice"), bob = user("bob");
    var original = createPost(alice, "moderation");
    int id = original.path("id").asInt();
    long comment = comment(bob, id, "comment", null);
    ok(call(post("/post/" + id + "/bookmark"), null, bob));
    moderation(id, "HIDDEN");
    call(get("/post/" + id), null, null).andExpect(status().isNotFound());
    call(get("/post/" + id + "/comments"), null, null).andExpect(status().isNotFound());
    call(post("/post/" + id + "/like"), null, bob).andExpect(status().isNotFound());
    assertThat(
            ok(call(get("/post/list?query=moderation"), null, null)).path("totalElements").asInt())
        .isZero();
    assertThat(ok(call(get("/me/bookmarks"), null, bob)).path("totalElements").asInt()).isZero();
    assertThat(ok(call(get("/tag/list"), null, null))).isEmpty();
    call(get("/me/posts/" + id), null, bob).andExpect(status().isForbidden());
    var owned = ok(call(get("/me/posts/" + id), null, alice));
    assertThat(owned.path("status").asText()).isEqualTo("HIDDEN");
    call(
            put("/post/" + id),
            Map.of(
                "title",
                "edited",
                "content",
                "text",
                "tags",
                List.of("social"),
                "version",
                original.path("version").asLong()),
            alice)
        .andExpect(status().isConflict());
    var edited =
        ok(
            call(
                put("/post/" + id),
                Map.of(
                    "title",
                    "edited",
                    "content",
                    "text",
                    "tags",
                    List.of("social"),
                    "version",
                    owned.path("version").asLong()),
                alice));
    assertThat(edited.path("status").asText()).isEqualTo("PENDING");
    moderation(id, "PUBLISHED");
    ok(call(get("/post/" + id), null, null));
    assertThat(ok(call(get("/me/bookmarks"), null, bob)).path("totalElements").asInt())
        .isEqualTo(1);
    ok(
        admin(
            put("/internal/admin/comments/" + comment + "/visibility"),
            Map.of("visible", false, "reason", "违规")));
    assertThat(ok(call(get("/post/" + id + "/comments"), null, null)).path("totalElements").asInt())
        .isZero();
    assertThat(ok(call(get("/post/" + id + "/interaction"), null, alice)).path("comments").asInt())
        .isZero();
  }

  @Test
  void disableAccountRevokesSessionsAndFiltersContributionsAndFollowerCounts() throws Exception {
    String alice = user("alice"), bob = user("bob");
    int id = createPost(alice, "account").path("id").asInt(),
        other = createPost(bob, "disabled-author").path("id").asInt();
    long root = comment(bob, id, "root", null);
    comment(alice, id, "reply", root);
    ok(call(post("/post/" + id + "/like"), null, bob));
    ok(call(post("/post/" + id + "/bookmark"), null, bob));
    ok(call(post("/users/alice/follow"), null, bob));
    ok(
        admin(
            put("/internal/admin/users/" + userId("bob") + "/status"),
            Map.of("status", false, "reason", "违规账号")));
    call(get("/ums/user/info"), null, bob).andExpect(status().isUnauthorized());
    call(get("/post/" + other), null, null).andExpect(status().isNotFound());
    var interaction = ok(call(get("/post/" + id + "/interaction"), null, alice));
    assertThat(interaction.path("comments").asInt()).isZero();
    assertThat(interaction.path("likes").asInt()).isZero();
    assertThat(interaction.path("bookmarks").asInt()).isZero();
    assertThat(ok(call(get("/me/followers"), null, alice)).path("totalElements").asInt()).isZero();
    call(post("/messages"), Map.of("username", "bob", "content", "disabled"), alice)
        .andExpect(status().isNotFound());
    ok(
        admin(
            put("/internal/admin/users/" + userId("bob") + "/status"),
            Map.of("status", true, "reason", "恢复")));
    call(get("/ums/user/info"), null, bob).andExpect(status().isUnauthorized());
    ok(call(get("/ums/user/info"), null, login("bob")));
    assertThat(ok(call(get("/post/" + id + "/interaction"), null, alice)).path("comments").asInt())
        .isEqualTo(2);
  }

  @Test
  void operationsCatalogCrudValidationAndAuditPreserveInUseTags() throws Exception {
    Map<String, Map<String, Object>> catalog =
        Map.of(
            "billboards", Map.of("content", "公告", "visible", true),
            "tips", Map.of("content", "提示", "author", "作者", "enabled", true),
            "promotions",
                Map.of("title", "资源", "link", "https://example.com/", "description", "说明"),
            "tags", Map.of("name", "OperatorTag"));
    for (var entry : catalog.entrySet()) {
      String path = "/internal/admin/" + entry.getKey();
      long id = ok(admin(post(path), entry.getValue())).path("id").asLong();
      ok(admin(get(path + "/" + id), null));
      ok(admin(put(path + "/" + id), entry.getValue()));
      ok(admin(delete(path + "/" + id), null));
      admin(get(path + "/" + id), null).andExpect(status().isNotFound());
    }
    admin(
            post("/internal/admin/promotions"),
            Map.of("title", "bad", "link", "javascript:alert(1)", "description", ""))
        .andExpect(status().isBadRequest());
    String alice = user("alice");
    createPost(alice, "tagged");
    long tag = db.queryForObject("SELECT id FROM bms_tag WHERE name='social'", Long.class);
    admin(delete("/internal/admin/tags/" + tag), null).andExpect(status().isConflict());
    var audit = ok(admin(get("/internal/admin/audit").param("query", "运营管理员"), null));
    assertThat(audit.path("totalElements").asInt()).isEqualTo(12);
    assertThat(audit.path("content").get(0).path("actor").asText()).isEqualTo("运营管理员");
    var users = ok(admin(get("/internal/admin/users"), null));
    assertThat(users.toString()).doesNotContain("password", "authVersion", "token");
  }

  @ParameterizedTest
  @CsvSource({"tips,content,author", "promotions,title,description"})
  void operationsCatalogSearchIncludesContentAndSecondaryFields(
      String kind, String primaryField, String secondaryField) throws Exception {
    String path = "/internal/admin/" + kind;
    var input = new LinkedHashMap<String, Object>();
    input.put(primaryField, "正文 Mixed_%! 内容");
    input.put(secondaryField, "其他字段");
    if (kind.equals("tips")) input.put("enabled", true);
    else input.put("link", "https://example.com/");
    long primaryMatch = ok(admin(post(path), input)).path("id").asLong();
    input.put(primaryField, "普通内容");
    input.put(secondaryField, "署名或描述 mIxEd_%!");
    long secondaryMatch = ok(admin(post(path), input)).path("id").asLong();
    input.put(primaryField, "mixedXY!%");
    input.put(secondaryField, "mixedXY!%");
    ok(admin(post(path), input));

    var first = ok(admin(get(path).param("query", "  MIXED_%!  ").param("size", "1"), null));
    assertThat(first.path("totalElements").asInt()).isEqualTo(2);
    assertThat(first.path("totalPages").asInt()).isEqualTo(2);
    assertThat(first.path("content").size()).isEqualTo(1);
    assertThat(first.path("content").get(0).path("id").asLong()).isEqualTo(secondaryMatch);
    var second =
        ok(admin(
            get(path).param("query", "  MIXED_%!  ").param("size", "1").param("pageNo", "2"), null));
    assertThat(second.path("totalElements").asInt()).isEqualTo(2);
    assertThat(second.path("content").size()).isEqualTo(1);
    assertThat(second.path("content").get(0).path("id").asLong()).isEqualTo(primaryMatch);
  }

  @Test
  void deletingPostCascadesSocialRowsAndMaintainsTagCounts() throws Exception {
    String alice = user("alice"), bob = user("bob");
    int id = createPost(alice, "delete").path("id").asInt();
    long root = comment(bob, id, "comment", null);
    comment(alice, id, "reply", root);
    ok(call(post("/post/" + id + "/like"), null, bob));
    ok(call(post("/post/" + id + "/bookmark"), null, bob));
    ok(admin(delete("/internal/admin/posts/" + id + "?reason=delete-check"), null));
    for (String table : List.of("bms_comment", "bms_post_like", "bms_bookmark", "ums_notification"))
      assertThat(
              db.queryForObject(
                  "SELECT COUNT(*) FROM " + table + " WHERE post_id=?", Integer.class, id))
          .isZero();
    assertThat(
            db.queryForObject("SELECT topic_count FROM bms_tag WHERE name='social'", Integer.class))
        .isZero();
  }

  @Test
  void newEndpointsEnforcePagingInputAndPerAccountRateLimits() throws Exception {
    String alice = user("alice"), bob = user("bob");
    int id = createPost(alice, "limits").path("id").asInt();
    for (String path :
        List.of(
            "/me/posts",
            "/me/bookmarks",
            "/me/following",
            "/me/followers",
            "/notifications",
            "/messages/conversations",
            "/messages?username=bob",
            "/post/" + id + "/comments")) {
      String separator = path.contains("?") ? "&" : "?";
      call(get(path + separator + "pageNo=0"), null, alice).andExpect(status().isBadRequest());
      call(get(path + separator + "size=51"), null, alice).andExpect(status().isBadRequest());
    }
    for (String path :
        List.of("users", "posts", "comments", "billboards", "tips", "promotions", "tags", "audit"))
      admin(get("/internal/admin/" + path + "?size=51"), null).andExpect(status().isBadRequest());
    call(post("/post/" + id + "/comments"), Map.of("content", " "), bob)
        .andExpect(status().isBadRequest());
    call(post("/messages"), Map.of("username", "alice", "content", "x".repeat(2001)), bob)
        .andExpect(status().isBadRequest());
    when(sessions.allow(anyString(), anyInt())).thenReturn(false);
    call(post("/post/" + id + "/comments"), Map.of("content", "blocked"), bob)
        .andExpect(status().isTooManyRequests());
    call(post("/post/" + id + "/like"), null, bob).andExpect(status().isTooManyRequests());
    call(post("/users/alice/follow"), null, bob).andExpect(status().isTooManyRequests());
    call(post("/messages"), Map.of("username", "alice", "content", "blocked"), bob)
        .andExpect(status().isTooManyRequests());
    assertThat(db.queryForObject("SELECT COUNT(*) FROM bms_comment", Integer.class)).isZero();
  }

  @Test
  void notificationPreviewsNeverSplitEmojiSurrogatePairs() throws Exception {
    String alice = user("alice"), bob = user("bob");
    int post = createPost(alice, "Unicode notification").path("id").asInt();
    comment(bob, post, "a".repeat(253) + "😀" + "more", null);
    String preview =
        ok(call(get("/notifications"), null, alice))
            .path("content")
            .get(0)
            .path("content")
            .asText();
    assertThat(preview).isEqualTo("a".repeat(253) + "😀");
    assertThat(preview.length()).isEqualTo(255);
    comment(bob, post, "a".repeat(254) + "😀" + "more", null);
    String boundary =
        ok(call(get("/notifications"), null, alice))
            .path("content")
            .get(0)
            .path("content")
            .asText();
    assertThat(boundary).isEqualTo("a".repeat(254));
  }
}
