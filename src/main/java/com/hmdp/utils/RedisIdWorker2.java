package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Redis全局ID自增工具类
 */
@Component
public class RedisIdWorker2 {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final long BEGIN_TIMESTAMP = 1681084800L;

    public RedisIdWorker2(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String prefixKey) {
        // 1.获取当前时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long nowTimeStamp = nowSecond - BEGIN_TIMESTAMP;
        // 2.获取序列号
        // 2.1获取当前日期，精确到天
        String data = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2自增长
        long count = stringRedisTemplate.opsForValue().increment("inc:" + prefixKey + ":" + data);

        // 3.拼接返回,高位时间戳 低位序列号, 将时间戳向左移32位，低32位都为0，"|" 或运算填充低32位
        return nowTimeStamp << 32 | count;
    }

    public static void main(String[] args) {

        LocalDateTime beginTimestamp = LocalDateTime.of(2023, 4, 10, 0, 0, 0);
        long epochSecond = beginTimestamp.toEpochSecond(ZoneOffset.UTC);
        System.out.println("epochSecond = " + epochSecond);

    }

}
