package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RedisConnectivityTest {

    @Autowired
    StringRedisTemplate redis;

    @Test
    void setGetShouldWork() {
        String key = "probe:test";
        redis.opsForValue().set(key, "ok", Duration.ofSeconds(30));
        String val = redis.opsForValue().get(key);
        assertThat(val).isEqualTo("ok");
    }
}
