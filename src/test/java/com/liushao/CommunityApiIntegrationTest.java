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
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "COMMUNITY_ADMIN_KEY=review-test-key-0123456789-abcdefghijklmnopqrstuvwxyz")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommunityApiIntegrationTest {
  static final String ADMIN_KEY = "review-test-key-0123456789-abcdefghijklmnopqrstuvwxyz";
  @Autowired MockMvc mvc;
  @Autowired TestRedisHealthIndicator redisHealth;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entities;
  @MockitoBean CommunitySessions sessions;
  Map<String, String> active = new HashMap<>();

  @BeforeEach
  void setup() {
    redisHealth.current = Health.up().build();
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

  ResultActions send(MockHttpServletRequestBuilder request, Object body, String token)
      throws Exception {
    entities.flush();
    entities.clear();
    if (body != null) request.contentType("application/json").content(json.writeValueAsBytes(body));
    if (token != null) request.header("Authorization", "Bearer " + token);
    return mvc.perform(request);
  }

  JsonNode data(ResultActions result) throws Exception {
    return json.readTree(
            result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
        .path("data");
  }

  Map<String, Object> registration(String name) {
    return Map.of(
        "name",
        name,
        "pass",
        "Community-pass-123",
        "checkPass",
        "Community-pass-123",
        "email",
        name + "@example.com");
  }

  String user(String name) throws Exception {
    send(post("/ums/user/register"), registration(name), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.password").doesNotExist())
        .andExpect(jsonPath("$.data.authVersion").doesNotExist());
    return login(name, "Community-pass-123");
  }

  String login(String name, String pass) throws Exception {
    return data(send(post("/ums/user/login"), Map.of("username", name, "password", pass), null))
        .path("token")
        .asText();
  }

  Map<String, Object> postInput(String title, String... tags) {
    return Map.of(
        "title",
        title,
        "content",
        "# Markdown\n正文 😀\n<script>alert('unsafe')</script>",
        "tags",
        List.of(tags));
  }

  JsonNode create(String token, String title, String... tags) throws Exception {
    return approve(data(send(post("/post/add"), postInput(title, tags), token)));
  }

  JsonNode approve(JsonNode post) throws Exception {
    return data(send(put("/internal/admin/posts/" + post.path("id").asInt() + "/moderation")
        .header("X-Community-Admin-Key", ADMIN_KEY).header("X-Community-Admin-Actor", "reviewer"),
        Map.of("status", "PUBLISHED", "top", false, "essence", false,
            "reason", "测试内容审核通过", "version", post.path("version").asLong()), null));
  }

  @Test
  void healthProbesArePublicAndDoNotRevealInfrastructure() throws Exception {
    for (String endpoint :
        List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
      mvc.perform(get(endpoint))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("UP"))
          .andExpect(jsonPath("$.components").doesNotExist())
          .andExpect(jsonPath("$.details").doesNotExist());
    }
    mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    mvc.perform(get("/actuator/configprops")).andExpect(status().isNotFound());
  }

  @Test
  void readinessFailsWhenRedisIsDownButLivenessStaysUp() throws Exception {
    redisHealth.current =
        Health.down().withDetail("private-host", "should-not-be-published").build();
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("DOWN"))
        .andExpect(jsonPath("$.components").doesNotExist())
        .andExpect(jsonPath("$.details").doesNotExist());
    mvc.perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void registerChecksPasswordsUsernameEmailAndNormalizesNames() throws Exception {
    var mismatch = new HashMap<>(registration("alice"));
    mismatch.put("checkPass", "different");
    send(post("/ums/user/register"), mismatch, null).andExpect(status().isBadRequest());
    String alice = user("Alice");
    send(get("/ums/user/info"), null, alice)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value("alice"));
    send(post("/ums/user/register"), registration("alice"), null).andExpect(status().isConflict());
    var duplicate = new HashMap<>(registration("bob"));
    duplicate.put("email", "ALICE@example.com");
    send(post("/ums/user/register"), duplicate, null).andExpect(status().isConflict());
    var invalid = new HashMap<>(registration("bad name"));
    send(post("/ums/user/register"), invalid, null).andExpect(status().isBadRequest());
    send(post("/ums/user/login"), Map.of("username", "alice", "password", "wrong"), null)
        .andExpect(status().isUnauthorized());
  }

  @Test
  void fullPostLifecycleHasTagsSearchAndVersionConflicts() throws Exception {
    String alice = user("alice");
    var post = create(alice, "第一篇主题", "Java", "java", "Vue");
    int id = post.path("id").asInt();
    assertThat(post.path("tags").size()).isEqualTo(2);
    assertThat(post.path("createTime").isNull()).isFalse();
    mvc.perform(get("/post/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.view").value(1))
        .andExpect(jsonPath("$.data.content").value(postInput("").get("content")));
    mvc.perform(get("/post/list?query=第一篇"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));
    mvc.perform(get("/post/list?query=alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));
    mvc.perform(get("/post/list?tag=java&tab=hot"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));
    var input = new HashMap<>(postInput("更新后的主题", "vue", "redis"));
    input.put("version", post.path("version").asLong());
    var updated = data(send(put("/post/" + id), input, alice));
    assertThat(updated.path("version").asLong()).isGreaterThan(post.path("version").asLong());
    assertThat(updated.path("status").asText()).isEqualTo("PENDING");
    mvc.perform(get("/post/" + id)).andExpect(status().isNotFound());
    approve(updated);
    send(put("/post/" + id), input, alice).andExpect(status().isConflict());
    mvc.perform(get("/post/list?tag=java"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
    assertThat(
            db.queryForObject("SELECT topic_count FROM bms_tag WHERE name='java'", Integer.class))
        .isZero();
    assertThat(db.queryForObject("SELECT topic_count FROM bms_tag WHERE name='vue'", Integer.class))
        .isEqualTo(1);
    send(delete("/post/" + id), null, alice).andExpect(status().isOk());
    mvc.perform(get("/post/" + id)).andExpect(status().isNotFound());
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM bms_post_tag WHERE topic_id=?", Integer.class, id))
        .isZero();
    assertThat(db.queryForObject("SELECT SUM(topic_count) FROM bms_tag", Integer.class)).isZero();
  }

  @Test
  void authorIsDerivedFromTokenAndForeignWritesAreForbidden() throws Exception {
    String alice = user("alice"), bob = user("bob");
    var post =
        data(
            send(
                post("/post/add").header("userName", "bob"),
                postInput("Alice's post", "java"),
                alice));
    int id = post.path("id").asInt();
    assertThat(post.path("username").asText()).isEqualTo("alice");
    var update = new HashMap<>(postInput("stolen"));
    update.put("version", post.path("version").asLong());
    send(put("/post/" + id), update, bob).andExpect(status().isForbidden());
    send(delete("/post/" + id), null, bob).andExpect(status().isForbidden());
    send(delete("/post/" + id), null, null).andExpect(status().isUnauthorized());
    send(post("/post/add"), postInput("anonymous"), null).andExpect(status().isUnauthorized());
    mvc.perform(get("/post/" + id)).andExpect(status().isNotFound());
    send(get("/me/posts/" + id), null, alice).andExpect(jsonPath("$.data.title").value("Alice's post"));
  }

  @Test
  void pagingHotSearchAndEmptyInputsHaveCorrectBoundaries() throws Exception {
    String token = user("writer");
    for (int i = 0; i < 3; i++) create(token, "主题 " + i, "java");
    mvc.perform(get("/post/list?pageNo=2&size=2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.totalElements").value(3));
    mvc.perform(get("/post/list?pageNo=99&size=2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content.length()").value(0))
        .andExpect(jsonPath("$.data.totalElements").value(3));
    mvc.perform(get("/post/list?pageNo=0")).andExpect(status().isBadRequest());
    mvc.perform(get("/post/list?size=51")).andExpect(status().isBadRequest());
    mvc.perform(get("/post/list?tab=unknown")).andExpect(status().isBadRequest());
    mvc.perform(get("/post/list?query=%25"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
    send(post("/post/add"), Map.of("title", " ", "content", "body", "tags", List.of()), token)
        .andExpect(status().isBadRequest());
    send(post("/post/add"), postInput("too many", "a", "b", "c", "d", "e", "f"), token)
        .andExpect(status().isBadRequest());
    db.update(
        "UPDATE bms_post SET create_time=?",
        java.sql.Timestamp.from(java.time.Instant.now().minus(Duration.ofDays(8))));
    mvc.perform(get("/post/list?tab=hot"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void profilesDoNotExposeContactDetailsAndOnlyUpdateOwnAccount() throws Exception {
    String alice = user("alice"), bob = user("bob");
    send(
            post("/ums/user/profile").header("userName", "bob"),
            Map.of("alias", "Alice新昵称", "bio", "个人简介", "id", 999),
            alice)
        .andExpect(status().isOk());
    mvc.perform(get("/ums/user/profile/alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.alias").value("Alice新昵称"))
        .andExpect(jsonPath("$.data.email").doesNotExist())
        .andExpect(jsonPath("$.data.mobile").doesNotExist())
        .andExpect(jsonPath("$.data.password").doesNotExist());
    send(get("/ums/user/info"), null, bob).andExpect(jsonPath("$.data.alias").value("bob"));
    mvc.perform(get("/ums/user/profile/missing")).andExpect(status().isNotFound());
  }

  @Test
  void passwordAndLogoutInvalidateSessionsAndDisabledUsersCannotWrite() throws Exception {
    String alice = user("alice");
    send(put("/ums/user/password"), Map.of(), alice).andExpect(status().isMethodNotAllowed());
    send(
            post("/ums/user/password"),
            Map.of("currentPass", "wrong", "password", "New-pass-123", "checkPass", "New-pass-123"),
            alice)
        .andExpect(status().isBadRequest());
    send(
            post("/ums/user/password"),
            Map.of(
                "currentPass",
                "Community-pass-123",
                "password",
                "New-pass-123",
                "checkPass",
                "New-pass-123"),
            alice)
        .andExpect(status().isOk());
    send(get("/ums/user/info"), null, alice).andExpect(status().isUnauthorized());
    String fresh = login("alice", "New-pass-123");
    send(post("/ums/user/logout"), null, fresh).andExpect(status().isOk());
    send(get("/ums/user/info"), null, fresh).andExpect(status().isUnauthorized());
    String disabled = login("alice", "New-pass-123");
    db.update("UPDATE ums_user SET status=FALSE WHERE username='alice'");
    // Clear persistence context so the filter observes the out-of-band administrative change.
    entityManager.clear();
    send(post("/post/add"), postInput("blocked"), disabled).andExpect(status().isUnauthorized());
  }

  @Test
  void localDevelopmentCorsAllowsBothLoopbackHosts() throws Exception {
    for (String origin : List.of("http://localhost:5173", "http://127.0.0.1:5173"))
      mvc.perform(
              options("/ums/user/register")
                  .header("Origin", origin)
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", origin));
    mvc.perform(
            options("/ums/user/register")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
  }

  @Autowired jakarta.persistence.EntityManager entityManager;

  @Test
  void rateLimitsReturn429AndEmptySidebarsRemainValid() throws Exception {
    when(sessions.allow(anyString(), anyInt())).thenReturn(false);
    send(post("/ums/user/register"), registration("alice"), null)
        .andExpect(status().isTooManyRequests());
    send(post("/ums/user/login"), Map.of("username", "alice", "password", "wrong"), null)
        .andExpect(status().isTooManyRequests());
    db.update("DELETE FROM bms_billboard");
    db.update("DELETE FROM bms_tip");
    mvc.perform(get("/billboard/show"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
    mvc.perform(get("/tip/today"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
    mvc.perform(get("/tag/list"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
  static class HealthTestConfiguration {
    @org.springframework.context.annotation.Bean
    TestRedisHealthIndicator redisHealthIndicator() {
      return new TestRedisHealthIndicator();
    }
  }

  static class TestRedisHealthIndicator implements HealthIndicator {
    Health current = Health.up().build();

    @Override
    public Health health() {
      return current;
    }
  }
}
