package com.liushao.security;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisCommunitySessions implements CommunitySessions {
  private final StringRedisTemplate redis;
  private static final String PREFIX = "liushao:community:";

  public RedisCommunitySessions(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public void save(String id, String username, Duration ttl) {
    redis.opsForValue().set(PREFIX + "session:" + id, username, ttl);
  }

  public boolean active(String id, String username) {
    return id != null && username.equals(redis.opsForValue().get(PREFIX + "session:" + id));
  }

  public void revoke(String id) {
    redis.delete(PREFIX + "session:" + id);
  }

  public boolean allow(String key, int limit) {
    var script =
        new DefaultRedisScript<Long>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end;"
                + " return n",
            Long.class);
    Long count = redis.execute(script, List.of(PREFIX + "rate:" + key));
    return count != null && count <= limit;
  }
}
