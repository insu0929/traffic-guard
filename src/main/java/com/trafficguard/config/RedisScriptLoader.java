package com.trafficguard.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Redis Lua 스크립트를 외부 파일에서 로드하는 유틸리티 클래스
 */
@Slf4j
public class RedisScriptLoader {

    /**
     * classpath의 scripts 디렉토리에서 Lua 스크립트를 로드합니다.
     *
     * @param scriptName 스크립트 파일명 (예: "token-bucket.lua")
     * @return DefaultRedisScript 객체
     */
    public static DefaultRedisScript<List<Object>> loadScript(String scriptName) {
        try {
            ClassPathResource resource = new ClassPathResource("scripts/" + scriptName);
            String scriptContent = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            
            DefaultRedisScript<List<Object>> script = new DefaultRedisScript<>();
            script.setResultType((Class<List<Object>>) (Class<?>) List.class);
            script.setScriptText(scriptContent);
            
            log.debug("Successfully loaded Redis script: {}", scriptName);
            return script;
        } catch (IOException e) {
            log.error("Failed to load Redis script: {}", scriptName, e);
            throw new RuntimeException("Failed to load Redis script: " + scriptName, e);
        }
    }

    /**
     * Token Bucket 스크립트를 로드합니다.
     *
     * @return Token Bucket용 DefaultRedisScript
     */
    public static DefaultRedisScript<List<Object>> loadTokenBucketScript() {
        return loadScript("token-bucket.lua");
    }
}


