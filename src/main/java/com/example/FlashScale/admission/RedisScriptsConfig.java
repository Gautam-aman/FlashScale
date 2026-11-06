package com.example.FlashScale.admission;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisScriptsConfig {

    private RedisScript<Long> load(String filename) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/" +  filename));
        script.setResultType(Long.class);
        return script;
    }

    @Bean("rateLimitScript")
    public RedisScript<Long> rateLimitScript() {
        return load("rate-limit.lua");
    }

    @Bean("acquirePermitScript")
    public RedisScript<Long> acquirePermitScript() {
        return load("acquire-permit.lua");
    }

    @Bean("releasePermitScript")
    public RedisScript<Long> releasePermitScript() {
        return load("release-permit.lua");
    }
}

