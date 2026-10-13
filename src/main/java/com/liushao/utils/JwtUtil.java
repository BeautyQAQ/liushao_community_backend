package com.liushao.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    public static final String USER_NAME = "userName";
    private final SecretKey key;
    private final long expire;
    public JwtUtil(@Value("${liushao.jwt.secret}") String secret,
                   @Value("${liushao.jwt.expire:86400}") long expire) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 64) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 64 UTF-8 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expire = expire;
    }
    public String generateToken(String username) {
        Date now = new Date();
        return Jwts.builder().subject(username).claim(USER_NAME, username).issuedAt(now)
            .expiration(new Date(now.getTime() + expire * 1000))
            .signWith(key, Jwts.SIG.HS512).compact();
    }
    public Claims getClaimByToken(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
    public boolean isTokenExpired(Claims claims) { return claims.getExpiration().before(new Date()); }
}
