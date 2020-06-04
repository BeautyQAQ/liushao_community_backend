package com.blog.shiro;

import lombok.Data;

import java.io.Serializable;

/**
 * @author SZ-UserBDG7
 */
@Data
public class AccountProfile implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String avatar;

    private String email;
}
