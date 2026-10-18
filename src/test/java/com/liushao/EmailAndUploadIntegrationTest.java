package com.liushao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.liushao.common.exception.ApiException;
import com.liushao.model.dto.ProfileRequests;
import com.liushao.security.CommunitySessions;
import com.liushao.service.CommunityMailer;
import com.liushao.service.ImageUploadService;
import com.liushao.service.UmsUserService;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailAndUploadIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired UmsUserService users;
  @Autowired ImageUploadService images;
  @Autowired PlatformTransactionManager transactions;
  @MockitoBean CommunitySessions sessions;
  @MockitoBean CommunityMailer mail;
  final Map<String, String> active = new HashMap<>(), codes = new HashMap<>();
  static final Path UPLOADS = temporaryDirectory();

  static Path temporaryDirectory() {
    try {
      return Files.createTempDirectory("liushao-upload-test-");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("app.uploads.directory", () -> UPLOADS.toString());
  }

  @AfterAll
  static void cleanFiles() throws IOException {
    try (var paths = Files.walk(UPLOADS)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
    }
  }

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
    doAnswer(
            a -> {
              codes.put(a.getArgument(0) + ":" + a.getArgument(1), a.getArgument(2));
              return null;
            })
        .when(mail)
        .sendCode(anyString(), anyString(), anyString());
  }

  ResultActions request(MockHttpServletRequestBuilder builder, Object body, String token)
      throws Exception {
    if (body != null) builder.contentType("application/json").content(json.writeValueAsBytes(body));
    if (token != null) builder.header("Authorization", "Bearer " + token);
    return mvc.perform(builder);
  }

  JsonNode data(ResultActions action) throws Exception {
    return json.readTree(
            action.andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
        .path("data");
  }

  record Account(String username, String email, String token, int id) {}

  Account account() throws Exception {
    String username = "mail_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
        email = username + "@example.com";
    int id =
        data(request(
                post("/ums/user/register"),
                Map.of(
                    "name",
                    username,
                    "email",
                    email,
                    "pass",
                    "Password-123",
                    "checkPass",
                    "Password-123"),
                null))
            .path("id")
            .asInt();
    String token =
        data(request(
                post("/ums/user/login"),
                Map.of("username", username, "password", "Password-123"),
                null))
            .path("token")
            .asText();
    return new Account(username, email, token, id);
  }

  String sendVerification(Account user) throws Exception {
    request(post("/ums/user/email/send"), Map.of(), user.token()).andExpect(status().isOk());
    return codes.get(user.email() + ":VERIFY");
  }

  String sendReset(Account user) throws Exception {
    request(post("/ums/user/password/forgot"), Map.of("email", user.email()), null)
        .andExpect(status().isOk());
    return codes.get(user.email() + ":RESET");
  }

  Map<String, String> reset(Account user, String code) {
    return Map.of(
        "email",
        user.email(),
        "code",
        code,
        "password",
        "New-password-456",
        "checkPass",
        "New-password-456");
  }

  @Test
  void verificationRequiresLoginHashesCodesExpiresAndConsumesOnce() throws Exception {
    Account user = account();
    request(post("/ums/user/email/send"), Map.of(), null).andExpect(status().isUnauthorized());
    String code = sendVerification(user);
    assertThat(code).matches("[0-9]{6}");
    assertThat(
            db.queryForObject(
                "SELECT code_hash FROM community_email_code WHERE user_id=? AND purpose='VERIFY'",
                String.class,
                user.id()))
        .hasSize(64)
        .isNotEqualTo(code);
    request(post("/ums/user/email/send"), Map.of(), user.token())
        .andExpect(status().isTooManyRequests());
    request(
            post("/ums/user/email/verify"),
            Map.of("code", code.equals("000000") ? "111111" : "000000"),
            user.token())
        .andExpect(status().isBadRequest());
    assertThat(
            db.queryForObject(
                "SELECT attempts FROM community_email_code WHERE user_id=? AND purpose='VERIFY'",
                Integer.class,
                user.id()))
        .isEqualTo(1);
    request(post("/ums/user/email/verify"), Map.of("code", code), user.token())
        .andExpect(status().isOk());
    request(get("/ums/user/info"), null, user.token())
        .andExpect(jsonPath("$.data.active").value(true));
    request(post("/ums/user/email/verify"), Map.of("code", code), user.token())
        .andExpect(status().isBadRequest());
    request(post("/ums/user/email/send"), Map.of(), user.token()).andExpect(status().isConflict());
  }

  @Test
  void resetIsPrivateOneUseAndRevokesAllPreviousSessions() throws Exception {
    Account user = account();
    String known =
        request(post("/ums/user/password/forgot"), Map.of("email", user.email()), null)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String unknown =
        request(post("/ums/user/password/forgot"), Map.of("email", "absent-" + user.email()), null)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(unknown).isEqualTo(known);
    String code = codes.get(user.email() + ":RESET");
    var bad = new HashMap<>(reset(user, code));
    bad.put("checkPass", "different-123");
    request(post("/ums/user/password/reset"), bad, null).andExpect(status().isBadRequest());
    request(post("/ums/user/password/reset"), reset(user, code), null).andExpect(status().isOk());
    request(get("/ums/user/info"), null, user.token()).andExpect(status().isUnauthorized());
    request(
            post("/ums/user/login"),
            Map.of("username", user.username(), "password", "Password-123"),
            null)
        .andExpect(status().isUnauthorized());
    request(
            post("/ums/user/login"),
            Map.of("username", user.username(), "password", "New-password-456"),
            null)
        .andExpect(status().isOk());
    request(post("/ums/user/password/reset"), reset(user, code), null)
        .andExpect(status().isBadRequest());
  }

  @Test
  void authenticatedPasswordChangeInvalidatesPreviouslyIssuedEmailCodes() throws Exception {
    Account user = account();
    String verification = sendVerification(user), resetCode = sendReset(user);
    request(
            post("/ums/user/password"),
            Map.of(
                "currentPass",
                "Password-123",
                "password",
                "Changed-password-789",
                "checkPass",
                "Changed-password-789"),
            user.token())
        .andExpect(status().isOk());
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM community_email_code WHERE user_id=?",
                Integer.class,
                user.id()))
        .isZero();
    request(post("/ums/user/password/reset"), reset(user, resetCode), null)
        .andExpect(status().isBadRequest());
    request(get("/ums/user/info"), null, user.token()).andExpect(status().isUnauthorized());
    String token =
        data(request(
                post("/ums/user/login"),
                Map.of("username", user.username(), "password", "Changed-password-789"),
                null))
            .path("token")
            .asText();
    request(post("/ums/user/email/verify"), Map.of("code", verification), token)
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"profile", "password", "avatar"})
  void userWritesPreserveConcurrentVerificationAndSessionRevocation(String operation)
      throws Exception {
    Account user = account();
    Runnable mutation = userMutation(user, operation);
    try (var executor = Executors.newSingleThreadExecutor()) {
      Future<?> pending = blockUserMutationThenChangeSecurityState(user, mutation, false, executor);
      pending.get(5, TimeUnit.SECONDS);
    }
    assertThat(
            db.queryForObject("SELECT active FROM ums_user WHERE id=?", Boolean.class, user.id()))
        .isTrue();
    assertThat(
            db.queryForObject(
                "SELECT auth_version FROM ums_user WHERE id=?", Long.class, user.id()))
        .isEqualTo(operation.equals("password") ? 2L : 1L);
    request(get("/ums/user/info"), null, user.token()).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(strings = {"profile", "password", "avatar"})
  void userWritesCannotRestoreAnAccountDisabledWhileWaitingForItsRow(String operation)
      throws Exception {
    Account user = account();
    Runnable mutation = userMutation(user, operation);
    try (var executor = Executors.newSingleThreadExecutor()) {
      Future<?> pending = blockUserMutationThenChangeSecurityState(user, mutation, true, executor);
      assertThatThrownBy(() -> pending.get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(ApiException.class);
    }
    assertThat(
            db.queryForObject("SELECT status FROM ums_user WHERE id=?", Boolean.class, user.id()))
        .isFalse();
    assertThat(
            db.queryForObject(
                "SELECT auth_version FROM ums_user WHERE id=?", Long.class, user.id()))
        .isEqualTo(1L);
    request(get("/ums/user/info"), null, user.token()).andExpect(status().isUnauthorized());
  }

  Runnable userMutation(Account user, String operation) throws IOException {
    return switch (operation) {
      case "profile" ->
          () ->
              users.updateProfile(
                  user.username(),
                  new ProfileRequests.Profile("Changed alias", "Changed biography"));
      case "password" ->
          () ->
              users.password(
                  user.username(),
                  new ProfileRequests.Password(
                      "Password-123", "Changed-password-789", "Changed-password-789"));
      case "avatar" -> {
        String url =
            images
                .upload(
                    user.username(),
                    new MockMultipartFile("file", "avatar.png", "image/png", png()))
                .get("url")
                .toString();
        yield () -> images.avatar(user.username(), url);
      }
      default -> throw new IllegalArgumentException(operation);
    };
  }

  Future<?> blockUserMutationThenChangeSecurityState(
      Account user, Runnable mutation, boolean disabled, ExecutorService executor) {
    return new TransactionTemplate(transactions)
        .execute(
            transaction -> {
              db.queryForObject(
                  "SELECT id FROM ums_user WHERE id=? FOR UPDATE", Integer.class, user.id());
              Future<?> pending = executor.submit(mutation);
              // Observe a real database lock wait, avoiding a timing-based assumption about thread
              // order.
              await()
                  .pollInterval(Duration.ofMillis(10))
                  .atMost(Duration.ofSeconds(5))
                  .until(
                      () ->
                          db.queryForObject(
                                  "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE"
                                      + " BLOCKER_ID IS NOT NULL",
                                  Integer.class)
                              > 0);
              db.update(
                  "UPDATE ums_user SET active=TRUE,auth_version=auth_version+1,status=? WHERE id=?",
                  !disabled,
                  user.id());
              return pending;
            });
  }

  @Test
  void failedAttemptsPersistAndExpiredCodesCannotResetPasswords() throws Exception {
    Account user = account();
    String code = sendReset(user);
    for (int i = 0; i < 5; i++)
      request(
              post("/ums/user/password/reset"),
              reset(user, code.equals("000000") ? "111111" : "000000"),
              null)
          .andExpect(status().isBadRequest());
    assertThat(
            db.queryForObject(
                "SELECT attempts FROM community_email_code WHERE user_id=? AND purpose='RESET'",
                Integer.class,
                user.id()))
        .isEqualTo(5);
    request(post("/ums/user/password/reset"), reset(user, code), null)
        .andExpect(status().isBadRequest());
    Account second = account();
    String expired = sendReset(second);
    db.update(
        "UPDATE community_email_code SET expires_at=? WHERE user_id=?",
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(10)),
        second.id());
    request(post("/ums/user/password/reset"), reset(second, expired), null)
        .andExpect(status().isBadRequest());
  }

  @Test
  void deliveryFailureRollsBackCodeAndDisabledAccountCannotReset() throws Exception {
    Account user = account();
    doThrow(new ApiException(503, "邮件暂时无法发送"))
        .when(mail)
        .sendCode(eq(user.email()), anyString(), anyString());
    request(post("/ums/user/email/send"), Map.of(), user.token())
        .andExpect(status().isServiceUnavailable());
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM community_email_code WHERE user_id=?",
                Integer.class,
                user.id()))
        .isZero();
    Account other = account();
    String code = sendReset(other);
    db.update("UPDATE ums_user SET status=FALSE WHERE id=?", other.id());
    request(post("/ums/user/password/reset"), reset(other, code), null)
        .andExpect(status().isBadRequest());
  }

  byte[] png() throws IOException {
    var image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
    try (var stream = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", stream);
      stream.write("untrusted-metadata".getBytes());
      return stream.toByteArray();
    }
  }

  @Test
  void imageIsDecodedRewrittenAndOnlyUploaderCanSetAvatar() throws Exception {
    Account alice = account(), bob = account();
    var part = new MockMultipartFile("file", "../../evil.svg", "image/svg+xml", png());
    String url =
        data(mvc.perform(
                multipart("/uploads/image")
                    .file(part)
                    .header("Authorization", "Bearer " + alice.token())))
            .path("url")
            .asText();
    assertThat(url).matches("/uploads/[a-f0-9-]+\\.png");
    byte[] stored =
        mvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1))
        .doesNotContain("untrusted-metadata");
    assertThat(ImageIO.read(new ByteArrayInputStream(stored)).getWidth()).isEqualTo(3);
    request(post("/ums/user/avatar"), Map.of("url", url), bob.token())
        .andExpect(status().isForbidden());
    request(post("/ums/user/avatar"), Map.of("url", url), alice.token())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.avatar").value(url));
    request(
            post("/ums/user/avatar"),
            Map.of("url", "https://evil.example/image.png"),
            alice.token())
        .andExpect(status().isBadRequest());
    mvc.perform(get("/uploads/not-a-file.png")).andExpect(status().isNotFound());
  }

  @Test
  void uploadRejectsUnauthenticatedActiveContentOversizeAndRateAbuse() throws Exception {
    Account user = account();
    var svg =
        new MockMultipartFile("file", "x.png", "image/png", "<svg onload='alert(1)'/>".getBytes());
    mvc.perform(multipart("/uploads/image").file(svg)).andExpect(status().isUnauthorized());
    mvc.perform(
            multipart("/uploads/image").file(svg).header("Authorization", "Bearer " + user.token()))
        .andExpect(status().isBadRequest());
    var huge = new MockMultipartFile("file", "x.png", "image/png", new byte[4 * 1024 * 1024 + 1]);
    mvc.perform(
            multipart("/uploads/image")
                .file(huge)
                .header("Authorization", "Bearer " + user.token()))
        .andExpect(status().isBadRequest());
    when(sessions.allow(eq("upload:" + user.username()), anyInt())).thenReturn(false);
    mvc.perform(
            multipart("/uploads/image")
                .file(new MockMultipartFile("file", "x.png", "image/png", png()))
                .header("Authorization", "Bearer " + user.token()))
        .andExpect(status().isTooManyRequests());
  }
}
