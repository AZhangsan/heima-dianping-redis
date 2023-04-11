package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁实现类
 */
public class SimpleRedisLock2 implements ILock {

    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final String PRE_LOCK = "lock:";
    private static final String ID_PRE = UUID.randomUUID().toString(true) + "-";

    private static final DefaultRedisScript<Long> SCRIPT;

    static {
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setLocation(new ClassPathResource("unlock2.lua"));
        SCRIPT.setResultType(Long.class);
    }

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
        // 将UUID和线程ID拼接作为value放入redis，增加标识，防止其他线程误删锁
        String value = ID_PRE + threadId;

        Boolean absent = stringRedisTemplate.opsForValue().setIfAbsent(getLockName(), value, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(absent);
    }

    @Override
    public void unlock() {
        // 将线程ID当做value存入redis
        long threadId = Thread.currentThread().getId();
        // 将UUID和线程ID拼接作为value放入redis，增加标识，防止其他线程误删锁
        String value = ID_PRE + threadId;

        stringRedisTemplate.execute(SCRIPT,
                Collections.singletonList(getLockName()),
                value);

    }

    /*
    @Override
    public void unlock() {
        // 将线程ID当做value存入redis
        long threadId = Thread.currentThread().getId();
        // 将UUID和线程ID拼接作为value放入redis，增加标识，防止其他线程误删锁
        String value = ID_PRE + threadId;

        String lockValue = stringRedisTemplate.opsForValue().get(getLockName());
        // 如果锁是自己的锁，则解锁
        if (value.equals(lockValue)) {
            stringRedisTemplate.delete(getLockName());
        }
    }*/
}
