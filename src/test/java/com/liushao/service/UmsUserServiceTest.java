package com.liushao.service;
import com.liushao.dao.UmsUserDao;
import com.liushao.model.dto.LoginDTO;
import com.liushao.model.entity.UmsUser;
import com.liushao.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class UmsUserServiceTest {
    private final UmsUserDao dao = mock(UmsUserDao.class);
    private final JwtUtil jwt = new JwtUtil("test-only-secret-abcdefghijklmnopqrstuvwxyz-0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZ", 60);
    private final UmsUserService service = new UmsUserService();
    private final LoginDTO login = new LoginDTO();
    private final UmsUser user = new UmsUser();
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "umsUserDao", dao);
        ReflectionTestUtils.setField(service, "jwtUtil", jwt);
        login.setUsername("alice"); login.setPassword("correct-password");
        user.setUsername("alice"); user.setPassword(new BCryptPasswordEncoder().encode("correct-password"));
        when(dao.findByUsername("alice")).thenReturn(user);
    }
    @Test void correctPasswordCreatesSignedToken() { assertEquals("alice", jwt.getClaimByToken(service.executeLogin(login)).getSubject()); }
    @Test void wrongPasswordIsRejected() { login.setPassword("wrong-password"); assertNull(service.executeLogin(login)); }
    @Test void missingUserIsRejected() { when(dao.findByUsername("alice")).thenReturn(null); assertNull(service.executeLogin(login)); }
    @Test void disabledUserIsRejected() { user.setStatus(false); assertNull(service.executeLogin(login)); }
}
