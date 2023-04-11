package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker2;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class RedisPreheat {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker2 redisIdWorker2;

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(500);

    @Test
    public void test() throws InterruptedException {
        List<Shop> list = shopService.list();
        for (Shop shop : list) {
            shopService.saveShopToRedis(shop.getId(), 60 * 24);
        }
        System.out.println("list.size() = " + list.size());
    }

    /**
     * 测试全局ID生成器
     *
     * @throws InterruptedException
     */
    @Test
    void testRandom() throws InterruptedException {

        CountDownLatch countDownLatch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long order = redisIdWorker2.nextId("order");
                System.out.println("order = " + order);
            }
            countDownLatch.countDown();
        };
        long beginTimeStamp = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            EXECUTOR_SERVICE.submit(task);
        }
        countDownLatch.await();
        long endTimeStamp = System.currentTimeMillis();
        long timeStamp = endTimeStamp - beginTimeStamp;
        System.out.println("timeStamp = " + timeStamp);
    }
}
