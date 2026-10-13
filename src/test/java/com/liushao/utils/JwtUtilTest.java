package com.liushao.utils;
import org.junit.jupiter.api.Test;
import io.jsonwebtoken.JwtException;
import static org.junit.jupiter.api.Assertions.*;
class JwtUtilTest {
    private static final String SECRET = "test-only-secret-abcdefghijklmnopqrstuvwxyz-0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    @Test void roundTrip() {
        JwtUtil jwt = new JwtUtil(SECRET, 60);
        assertEquals("alice", jwt.getClaimByToken(jwt.generateToken("alice")).getSubject());
    }
    @Test void expiredTokenIsRejected() {
        JwtUtil jwt = new JwtUtil(SECRET, -60);
        assertThrows(JwtException.class, () -> jwt.getClaimByToken(jwt.generateToken("alice")));
    }
    @Test void wrongSignatureIsRejected() {
        JwtUtil jwt = new JwtUtil(SECRET, 60);
        String token = new JwtUtil(SECRET + "different", 60).generateToken("alice");
        assertThrows(JwtException.class, () -> jwt.getClaimByToken(token));
    }
    @Test void weakSecretIsRejected() { assertThrows(IllegalArgumentException.class, () -> new JwtUtil("weak", 60)); }
}
