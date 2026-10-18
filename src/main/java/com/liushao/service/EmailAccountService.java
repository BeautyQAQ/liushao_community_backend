package com.liushao.service;

import com.liushao.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailAccountService {
  private final JdbcTemplate db;
  private final CommunityMailer mail;
  private final byte[] secret;
  private final SecureRandom random = new SecureRandom();
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

  public EmailAccountService(JdbcTemplate db, CommunityMailer mail,
      @Value("${liushao.jwt.secret}") String secret) {
    this.db = db;
    this.mail = mail;
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  @Transactional
  public void sendVerification(String username) {
    mail.requireConfigured();
    var user = userBy("username", username);
    if (Boolean.TRUE.equals(user.get("active"))) throw new ApiException(409, "邮箱已验证");
    issue(user, "VERIFY");
  }

  @Transactional
  public void forgot(String email) {
    mail.requireConfigured();
    var users = db.queryForList("SELECT id,email,status FROM ums_user WHERE email=? FOR UPDATE", normalize(email));
    if (!users.isEmpty() && Boolean.TRUE.equals(users.getFirst().get("status"))) issue(users.getFirst(), "RESET");
  }

  private void issue(Map<String,Object> user, String purpose) {
    int id = ((Number) user.get("id")).intValue();
    var rows = db.queryForList("SELECT created_at FROM community_email_code WHERE user_id=? AND purpose=?", id, purpose);
    if (!rows.isEmpty() && ((Timestamp) rows.getFirst().get("created_at")).toInstant().plusSeconds(60).isAfter(Instant.now())) {
      // Forgot-password responses must not disclose whether an account exists or is throttled.
      if (purpose.equals("RESET")) return;
      throw new ApiException(429, "请一分钟后再发送验证码");
    }
    String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    var now = Instant.now();
    db.update("DELETE FROM community_email_code WHERE user_id=? AND purpose=?", id, purpose);
    db.update("INSERT INTO community_email_code(user_id,purpose,code_hash,expires_at,created_at,attempts) VALUES(?,?,?,?,?,0)",
        id, purpose, hash(id, purpose, code), Timestamp.from(now.plusSeconds(600)), Timestamp.from(now));
    mail.sendCode(user.get("email").toString(), purpose, code);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public void verify(String username, String code) {
    var user = userBy("username", username);
    int id = ((Number) user.get("id")).intValue();
    consume(id, "VERIFY", code);
    db.update("UPDATE ums_user SET active=TRUE,modify_time=CURRENT_TIMESTAMP WHERE id=?", id);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public void reset(String email, String code, String password, String confirmation) {
    if (!password.equals(confirmation)) throw new ApiException(400, "两次输入的密码不一致");
    UmsUserService.checkPassword(password);
    var rows = db.queryForList("SELECT id,status FROM ums_user WHERE email=? FOR UPDATE", normalize(email));
    if (rows.isEmpty() || !Boolean.TRUE.equals(rows.getFirst().get("status"))) invalid();
    int id = ((Number) rows.getFirst().get("id")).intValue();
    consume(id, "RESET", code);
    db.update("UPDATE ums_user SET password=?,auth_version=auth_version+1,modify_time=CURRENT_TIMESTAMP WHERE id=?", passwords.encode(password), id);
    db.update("DELETE FROM community_email_code WHERE user_id=?", id);
  }

  private Map<String,Object> userBy(String field, String value) {
    // field is a server constant, never a request parameter.
    var users = db.queryForList("SELECT id,email,active,status FROM ums_user WHERE " + field + "=? FOR UPDATE", value);
    if (users.isEmpty() || !Boolean.TRUE.equals(users.getFirst().get("status"))) throw new ApiException(401, "请重新登录");
    return users.getFirst();
  }

  private void consume(int id, String purpose, String code) {
    var rows = db.queryForList("SELECT code_hash,expires_at,attempts FROM community_email_code WHERE user_id=? AND purpose=? FOR UPDATE", id, purpose);
    if (rows.isEmpty()) invalid();
    var row = rows.getFirst();
    if (((Number)row.get("attempts")).intValue() >= 5 || !((Timestamp)row.get("expires_at")).toInstant().isAfter(Instant.now())) invalid();
    if (!MessageDigest.isEqual(row.get("code_hash").toString().getBytes(StandardCharsets.US_ASCII), hash(id,purpose,code).getBytes(StandardCharsets.US_ASCII))) {
      db.update("UPDATE community_email_code SET attempts=attempts+1 WHERE user_id=? AND purpose=?",id,purpose);
      invalid();
    }
    db.update("DELETE FROM community_email_code WHERE user_id=? AND purpose=?", id, purpose);
  }

  private String hash(int id, String purpose, String code) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(("email:"+id+":"+purpose+":"+code).getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
  }

  private static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
  private static void invalid() { throw new ApiException(400, "验证码无效、已过期或尝试次数过多，请重新获取"); }
}
