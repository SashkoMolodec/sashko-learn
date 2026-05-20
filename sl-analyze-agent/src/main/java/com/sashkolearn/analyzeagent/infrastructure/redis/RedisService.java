package com.sashkolearn.analyzeagent.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void set(String key, String value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        log.debug("Set key: {} with TTL: {}s", key, ttlSeconds);
    }

    public String get(String key) {
        String value = redisTemplate.opsForValue().get(key);
        log.debug("Get key: {} -> {}", key, value != null ? "found" : "not found");
        return value;
    }

    public void delete(String key) {
        redisTemplate.delete(key);
        log.debug("Deleted key: {}", key);
    }

    public void setHash(String key, Map<String, String> values, long ttlSeconds) {
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        log.debug("Set hash key: {} with {} fields and TTL: {}s", key, values.size(), ttlSeconds);
    }

    public Map<Object, Object> getHash(String key) {
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
        log.debug("Get hash key: {} -> {} fields", key, hash.size());
        return hash;
    }

    public <T> void setObject(String key, T object, long ttlSeconds) {
        try {
            String json = objectMapper.writeValueAsString(object);
            set(key, json, ttlSeconds);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object for key: {}", key, e);
            throw new RuntimeException("Redis serialization error", e);
        }
    }

    public <T> T getObject(String key, TypeReference<T> typeReference) {
        String json = get(key);
        if (json == null) {
            return null;
        }

        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize object for key: {}", key, e);
            return null;
        }
    }

    public boolean exists(String key) {
        Boolean exists = redisTemplate.hasKey(key);
        return exists != null && exists;
    }
}
