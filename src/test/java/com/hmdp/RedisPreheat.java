package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.RedisIdWorker2;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class RedisPreheat {

    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private UserServiceImpl userService;

    @Resource
    private RedisIdWorker2 redisIdWorker2;

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(500);

    /**
     * 预热商铺信息
     *
     * @throws InterruptedException
     */
    @Test
    public void testShop() throws InterruptedException {
        List<Shop> list = shopService.list();
        for (Shop shop : list) {
            shopService.saveShopToRedis(shop.getId(), 60 * 24 * 15);
        }
        System.out.println("list.size() = " + list.size());
    }

    /**
     * 生成token加载到redis中 并将写入到token.txt文件中
     */
    @Test
    public void loadTokenToRedis() throws IOException {
        // 1.创建FileWriter对象
        FileWriter fw = new FileWriter("/{projectPath}/heima-dianping-redis/src/main/resources/token.txt");
        // 2.调用write方法写数据
        List<User> list = userService.list();
        for (User user : list) {
            // 生成token写入redis
            String token = userService.generateToken(user);
            fw.write(token);
            fw.write("\n");
        }
        // 3.关闭数据流
        fw.close();

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
