package com.trafficguard.util;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtil {
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Gson fulfillmentGson;

    /**
     * redis 저장 시 설정 timeout timeUnit 만큼 메모리에 존재
     *
     */
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit timeUnit) {
        if (key == null || value == null || timeUnit == null) {
            return false;
        }
        String storeValue = value instanceof String ? (String) value : fulfillmentGson.toJson(value);
        return stringRedisTemplate.opsForValue().setIfAbsent(key, storeValue, timeout, timeUnit);
    }

    public Boolean setAtomic(String key, Object value, long timeout, TimeUnit timeUnit) {
        if (key == null || value == null || timeUnit == null) {
            return true;
        }
        Object rtn  = true;
        try {
            rtn = stringRedisTemplate.execute(new SessionCallback(){
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    try{
                        operations.watch(key);
                        operations.multi();
                        String storeValue = value instanceof String ? (String) value : fulfillmentGson.toJson(value);
                        operations.opsForValue().get(key);
                        operations.opsForValue().set(key, storeValue, timeout, timeUnit);
                    } catch (Exception e){
                        operations.discard();
                        return true;
                    }
                    List<Object> objList =  operations.exec();
                    if(CollectionUtils.isEmpty(objList)){
                        return true;
                    } else {
                        if(objList.get(0) == null){
                            return true;
                        } else {
                            return false;
                        }
                    }
                }
            });

        }catch (Exception e ) {
            log.error(e.getLocalizedMessage());
            return true;
        }
        return (Boolean) rtn;
    }

    /**
     * redis 저장 시 설정 timeout timeUnit 만큼 메모리에 존재
     *
     */
    public void setByGson(String key, Object value, long timeout, TimeUnit timeUnit) {
        if (key == null || value == null || timeUnit == null) {
            return;
        }

        String storeValue = value instanceof String ? (String) value : fulfillmentGson.toJson(value);
        stringRedisTemplate.opsForValue().set(key, storeValue, timeout, timeUnit);
    }

    public <T> T getByGson(String key, Class<T> clazz) {
        if (key == null || clazz == null) {
            return null;
        }

        String value = stringRedisTemplate.opsForValue().get(key);

        try {
            @SuppressWarnings("unchecked")
            T object = clazz == String.class ? (T) value : fulfillmentGson.fromJson(value, clazz);
            return object;
        } catch (Exception e) {
            log.warn("getByGson error: {}", e.toString());
            return null;
        }
    }

    /**
     * Redis에서 key 에 해당하는 Object를 조회.
     *
     * @param key
     * @return
     */
    public <T> T getByGson(String key, Type type) {
        if (key == null || type == null) {
            return null;
        }

        String value = stringRedisTemplate.opsForValue().get(key);

        try {
            @SuppressWarnings("unchecked")
            T object = type.equals(String.class) ? (T) value : fulfillmentGson.fromJson(value, type);
            return object;
        } catch (Exception e) {
            log.warn("getByGson error: {}", e.toString());
            return null;
        }
    }

    /**
     * 만료 시간 조회
     * @return seconds, -1: 무제한, -2: 없음, -3: used in pipeline / transaction
     */
    public long getExpire(String key) {
        if (key == null) {
            return -2;
        }

        Long expire = redisTemplate.getExpire(key);
        if (expire == null) {
            return -3;
        }

        return expire.longValue();
    }

    /**
     * Redis에서 key를 삭제
     *
     * @param key
     */
    public boolean delete(String key){
        try {
            redisTemplate.opsForValue().getOperations().delete(key);
            return true;
        }catch (Exception ex){
            log.error("Redis delete error : "+ ex.getMessage(), ex);
            return false;
        }
    }
}



