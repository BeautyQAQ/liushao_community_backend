package com.liushao.service;

import com.liushao.common.exception.ApiException;
import com.liushao.dao.UmsUserDao;
import com.liushao.model.dto.*;
import com.liushao.model.entity.UmsUser;
import com.liushao.security.CommunitySessions;
import com.liushao.utils.JwtUtil;
import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UmsUserService {
  @Resource private UmsUserDao umsUserDao;
  @Resource private JwtUtil jwtUtil;
  @Resource private CommunitySessions sessions;
  @Resource private JdbcTemplate db;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
  private final String dummy = passwords.encode(UUID.randomUUID().toString());

  public UmsUser findUserByUsername(String username) {
    return umsUserDao.findByUsername(username);
  }

  public UmsUser requireUser(String username) {
    var user = findUserByUsername(username);
    if (user == null || !Boolean.TRUE.equals(user.getStatus()))
      throw new ApiException(404, "用户不存在");
    return user;
  }

  /** Serialize JPA writes with account verification, password resets, and moderation. */
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public UmsUser requireUserForUpdate(String username) {
    var user = umsUserDao.findLockedByUsername(username);
    if (user == null || !Boolean.TRUE.equals(user.getStatus()))
      throw new ApiException(404, "用户不存在");
    return user;
  }

  public String executeLogin(LoginDTO dto) {
    var user = findUserByUsername(dto.getUsername().trim().toLowerCase(Locale.ROOT));
    boolean matches =
        passwords.matches(dto.getPassword(), user == null ? dummy : user.getPassword());
    if (user == null || !matches || !Boolean.TRUE.equals(user.getStatus())) return null;
    String token = jwtUtil.generateToken(user.getUsername(), user.getAuthVersion());
    var claims = jwtUtil.getClaimByToken(token);
    sessions.save(
        claims.getId(),
        user.getUsername(),
        Duration.between(Instant.now(), claims.getExpiration().toInstant()));
    return token;
  }

  @Transactional
  public UmsUser executeRegister(RegisterDTO dto) {
    if (!dto.getPass().equals(dto.getCheckPass())) throw new ApiException(400, "两次输入的密码不一致");
    checkPassword(dto.getPass());
    String name = dto.getName().toLowerCase(Locale.ROOT),
        email = dto.getEmail().trim().toLowerCase(Locale.ROOT);
    if (umsUserDao.countByUsername(name) > 0) throw new ApiException(409, "账号已存在");
    if (umsUserDao.existsByEmail(email)) throw new ApiException(409, "邮箱已被注册");
    var user = new UmsUser();
    user.setUsername(name);
    user.setAlias(dto.getName());
    user.setEmail(email);
    user.setPassword(passwords.encode(dto.getPass()));
    user.setCreateTime(new Date());
    user.setModifyTime(new Date());
    return umsUserDao.saveAndFlush(user);
  }

  @Transactional
  public UmsUser updateProfile(String username, ProfileRequests.Profile input) {
    var user = requireUserForUpdate(username);
    user.setAlias(input.alias().trim());
    user.setBio(Objects.toString(input.bio(), "").trim());
    user.setModifyTime(new Date());
    return umsUserDao.save(user);
  }

  @Transactional
  public void password(String username, ProfileRequests.Password input) {
    var user = requireUserForUpdate(username);
    if (!passwords.matches(input.currentPass(), user.getPassword()))
      throw new ApiException(400, "当前密码不正确");
    if (!input.password().equals(input.checkPass())) throw new ApiException(400, "两次输入的密码不一致");
    checkPassword(input.password());
    user.setPassword(passwords.encode(input.password()));
    user.setAuthVersion(user.getAuthVersion() + 1);
    user.setModifyTime(new Date());
    umsUserDao.save(user);
    db.update("DELETE FROM community_email_code WHERE user_id=?", user.getId());
  }

  public Map<String, Object> publicProfile(String username) {
    var user = requireUser(username);
    var result = new LinkedHashMap<String, Object>();
    result.put("id", user.getId());
    result.put("username", user.getUsername());
    result.put("alias", user.getAlias());
    result.put("bio", user.getBio());
    result.put("avatar", user.getAvatar());
    result.put("createTime", user.getCreateTime());
    return result;
  }

  public static void checkPassword(String value) {
    if (value.length() < 8 || value.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ApiException(400, "密码至少 8 个字符，最多 72 字节");
  }
}
