package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class RedisPreheat {

    @Resource
    private ShopServiceImpl shopService;

    @Test
    public void test() throws InterruptedException {
        shopService.saveShopToRedis(1L, 10L);
    }


}
