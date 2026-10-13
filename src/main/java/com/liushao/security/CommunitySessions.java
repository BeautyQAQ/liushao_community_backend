package com.liushao.security;

import java.time.Duration;

public interface CommunitySessions {
  void save(String id, String username, Duration ttl);

  boolean active(String id, String username);

  void revoke(String id);

  boolean allow(String key, int limit);
}
