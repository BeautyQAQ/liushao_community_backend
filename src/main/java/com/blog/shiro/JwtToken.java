package com.blog.shiro;

import org.apache.shiro.authc.AuthenticationToken;

/**
 * @author SZ-UserBDG7
 */
public class JwtToken implements AuthenticationToken {
    private String token;

    public JwtToken(String jwt) {
        this.token = jwt;
    }

    @Override
    public Object getPrincipal() {
        return token;
    }

    @Override
    public Object getCredentials() {
        return token;
    }
}
