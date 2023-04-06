package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String cacheListKey = RedisConstants.CACHE_SHOP_TYPE_LIST;
        // 查看redis中是否有数据
        Long size = stringRedisTemplate.opsForList().size(cacheListKey);
        List<ShopType> shopTypes = new ArrayList<>();
        // 存在直接返回
        if (size != null && size > 0) {
            List<String> cacheQueryTypeList = stringRedisTemplate.opsForList().range(cacheListKey, 0, size - 1);
            if (cacheQueryTypeList != null) {
                shopTypes = cacheQueryTypeList.stream().map(x -> JSONUtil.toBean(x, ShopType.class)).collect(Collectors.toList());
            }
            return Result.ok(shopTypes);
        }
        // 不存在进行数据库查询
        shopTypes = query().orderByAsc("sort").list();
        // 数据库查询完，不存在返回空数据
        if (CollectionUtil.isEmpty(shopTypes)) {
            return Result.ok(shopTypes);
        }
        List<String> redisShopTypeList = shopTypes.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList());
        // 有数据将数据缓存至redis中
        stringRedisTemplate.opsForList().rightPushAll(cacheListKey, redisShopTypeList);
        stringRedisTemplate.expire(cacheListKey, 2, TimeUnit.HOURS);
        return Result.ok(shopTypes);
    }
}
