package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 封装redis缓存逻辑删除对象
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;

}
