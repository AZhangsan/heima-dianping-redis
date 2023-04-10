package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient2;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_EXECUTOR = Executors.newFixedThreadPool(10);

    @Resource(name = "cacheClient2")
    private CacheClient2 cacheClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//        Map<Object,Object> shop = queryWithPassThrough(id);
//        Shop shop = cacheClient.getRedisPenetrate(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);


        // 基于redis互斥锁解决缓存击穿
//        Map<Object, Object> shop = queryWithMutex(id);
        Shop shop = cacheClient.getRedisBreakdown(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        // 基于逻辑删除方式解决缓存击穿
//        Map shop = queryWithExpireTime(id);
        return Result.ok(shop);

    }

    // 缓存穿透
    private Map queryWithPassThrough(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        // 1.从redis中查询商铺信息
        Map<Object, Object> shopCache = stringRedisTemplate.opsForHash().entries(shopKey);
        // 2.判断redis中商户是否存在
        if (!shopCache.isEmpty()) {
            // -1代表缓存与数据库都不存在，防止缓存穿透
            if ("-1".equals(shopCache.get("id").toString())) {
                log.info("店铺信息不存在{}", shopCache);
                return null;
            }
            return shopCache;
        }
        // 3.缓存未命中
        // 4.根据id查询数据库
        Shop shop = getById(id);
        // 5.判断商户是否存在
        // 6.mysql中商铺不存在
        if (shop == null) {
            stringRedisTemplate.opsForHash().putAll(shopKey, new HashMap<String, String>() {{
                put("id", "-1");
            }});
            stringRedisTemplate.expire(shopKey, 1, TimeUnit.MINUTES);
            return null;
        }
        Map<String, Object> shopMap = BeanUtil.beanToMap(shop, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue != null ? fieldValue.toString() : "0"));

        stringRedisTemplate.opsForHash().putAll(shopKey, shopMap);
        stringRedisTemplate.expire(shopKey, 1, TimeUnit.HOURS);
        // 7.返回
        return shopMap;
    }

    // 获取互斥锁
    private Map queryWithMutex(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        // 1.从redis中查询商铺信息
        Map<Object, Object> shopCache = stringRedisTemplate.opsForHash().entries(shopKey);
        // 2.判断redis中商户是否存在
        if (!shopCache.isEmpty()) {
            // -1代表缓存与数据库都不存在，防止缓存击穿
            if ("-1".equals(shopCache.get("id").toString())) {
                log.info("店铺信息不存在{}", shopCache);
                return null;
            }
            return shopCache;
        }
        // 获取互斥锁
        String lockKey = SHOP_LOCK_KEY + id;
        Map<String, Object> shopMap = null;
        try {
            boolean tryLock = tryLock(lockKey);
            if (!tryLock) {
                Thread.sleep(20);
                return queryWithMutex(id);
            }
            Thread.sleep(100);
            // 3.缓存未命中
            // 4.根据id查询数据库
            Shop shop = query().eq("id", id).one();
            // 5.判断商户是否存在
            // 6.mysql中商铺不存在
            if (shop == null) {
                stringRedisTemplate.opsForHash().putAll(shopKey, new HashMap<String, String>() {{
                    put("id", "-1");
                }});
                stringRedisTemplate.expire(shopKey, 1, TimeUnit.MINUTES);
                return null;
            }
            shopMap = BeanUtil.beanToMap(shop, new HashMap<>(), CopyOptions.create()
                    .setIgnoreNullValue(true)
                    .setFieldValueEditor((fieldName, fieldValue) -> fieldValue != null ? fieldValue.toString() : "0"));

            stringRedisTemplate.opsForHash().putAll(shopKey, shopMap);
            stringRedisTemplate.expire(shopKey, 1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 解锁
            unLock(lockKey);
        }
        // 7.返回
        return shopMap;
    }

    private Map queryWithExpireTime(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        String lockKey = SHOP_LOCK_KEY + id;
        // 1.从redis中查询商铺信息
        Map<Object, Object> redisDataMap = stringRedisTemplate.opsForHash().entries(shopKey);
        // 2.判断redis中商户是否存在
        // 不存在 构建缓存
        if (redisDataMap.isEmpty()) {
            CACHE_EXECUTOR.submit(() -> {
                try {
                    saveShopToRedis(id, 20L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            return queryWithExpireTime(id);
        }

        // 将redis中数据格式转换为RedisData‘’
        RedisData redisData = BeanUtil.mapToBean(redisDataMap, RedisData.class, true, CopyOptions.create());
        Map shop = JSONUtil.toBean(redisData.getData().toString(), Map.class);
        // -1代表缓存与数据库都不存在，防止缓存击穿
        if ("-1".equals(shop.get("id").toString())) {
            log.info("店铺信息不存在{}", redisData);
            return null;
        }
        // 判断缓存是否过期 过期尝试获取锁
        if (redisData.getExpireTime() != null && LocalDateTime.now().isAfter(redisData.getExpireTime())) {
            boolean tryLock = tryLock(lockKey);
            if (tryLock) {
                CACHE_EXECUTOR.submit(() -> {
                    try {
                        log.debug("---获取锁，开始缓存构建---");
                        saveShopToRedis(id, 20L);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } finally {
                        log.debug("---释放锁---");
                        unLock(lockKey);
                    }
                });
            }
            // 未拿到锁 直接返回缓存数据
            return shop;
        }
        // 未过期 取缓存数据
        return shop;

    }

    private boolean tryLock(String lockKey) {
        return BooleanUtil.isTrue(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 2, TimeUnit.SECONDS));
    }

    private boolean unLock(String lockKey) {
        return BooleanUtil.isTrue(stringRedisTemplate.delete(lockKey));
    }

    /**
     * 将数据缓存到redis 设置逻辑过期时间
     *
     * @param id
     * @param expireSecond
     */
    public void saveShopToRedis(Long id, long expireSecond) throws InterruptedException {
        // 查询数据
        Shop shop = getById(id);
        // 封装逻辑时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecond));

        Thread.sleep(100);
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(redisData, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldNam, fieldValue) -> fieldValue.toString()
                        ));
        String shopKey = CACHE_SHOP_KEY + id;
        // 写入redis
//        stringRedisTemplate.opsForHash().putAll(shopKey, stringObjectMap);
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(redisData), expireSecond);


    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }

    @Override
    @Transactional
    public Result update2(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("更新ID不能为空");
        }
        String shopKey = "cache:shop:" + shop.getId();
        // 更新数据库
        updateById(shop);
        // 更新redis
        stringRedisTemplate.delete(shopKey);
        return Result.ok("更新成功");
    }
}
