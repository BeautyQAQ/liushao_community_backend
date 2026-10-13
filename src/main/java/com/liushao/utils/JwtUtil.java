package com.liushao.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
  public static final String USER_NAME = "userName";
  private final SecretKey key;
  private final long expire;

  public JwtUtil(
      @Value("${liushao.jwt.secret}") String secret,
      @Value("${liushao.jwt.expire:86400}") long expire) {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 64) {
      throw new IllegalArgumentException("JWT_SECRET must contain at least 64 UTF-8 bytes");
    }
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expire = expire;
  }

  public String generateToken(String username) {
    return generateToken(username, 0L);
  }

  public String generateToken(String username, long version) {
    Date now = new Date();
    return Jwts.builder()
        .id(java.util.UUID.randomUUID().toString())
        .claim("version", version)
        .subject(username)
        .claim(USER_NAME, username)
        .issuedAt(now)
        .expiration(new Date(now.getTime() + expire * 1000))
        .signWith(key, Jwts.SIG.HS512)
        .compact();
  }

  public Claims getClaimByToken(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  public boolean isTokenExpired(Claims claims) {
    return claims.getExpiration().before(new Date());
  }
}
