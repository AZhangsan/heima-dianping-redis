package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
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
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

//    @Resource
//    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//        Map<Object,Object> shop = queryWithPassThrough(id);
        Map<Object, Object> shop = queryWithMutex(id);

        return Result.ok(shop);

    }

    // 缓存穿透
    private Map queryWithPassThrough(Long id) {
        String shopKey = "cache:shop:" + id;
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
        String shopKey = "cache:shop:" + id;
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
        String lockKey = "cache:shop:lock:" + id;
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

    private boolean tryLock(String lockKey) {
        return BooleanUtil.isTrue(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 2, TimeUnit.SECONDS));
    }

    private boolean unLock(String lockKey) {
        return BooleanUtil.isTrue(stringRedisTemplate.delete(lockKey));
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
