package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁实现类
 */
public class SimpleRedisLock2 implements ILock {

    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final String PRE_LOCK = "lock:";

    private String getLockName() {
        return PRE_LOCK + name;
    }

    public SimpleRedisLock2(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 将线程ID当做value存入redis
        long threadId = Thread.currentThread().getId();
        Boolean absent = stringRedisTemplate.opsForValue().setIfAbsent(getLockName(), String.valueOf(threadId), timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(absent);
    }

    @Override
    public void unlock() {
        stringRedisTemplate.delete(getLockName());
    }
}
