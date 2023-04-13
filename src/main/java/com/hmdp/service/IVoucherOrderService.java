package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    /**
     * 使用synchronized解决库存超卖
     *
     * @param voucherId
     * @return
     */
    Result seckillVoucherWithSynchronized(Long voucherId);

    /**
     * 使用乐观锁思想解决库存超卖问题
     * update时候stock>0
     *
     * @param voucherId
     * @return
     */
    Result seckillVoucherWithStockMysql(Long voucherId);

    /**
     * 一人一单功能实现
     *
     * @param voucherId
     * @return
     */
    Result seckillVoucherByUser(Long voucherId);

    /**
     * 通过redis实现分布式锁解决库存超卖问题
     *
     * @param voucherId
     * @return
     */
    Result seckillVoucherWithRedis(Long voucherId);

    /**
     * 创建优惠券秒杀订单 重新获取IVoucherOrderService代理对象使事务生效
     *
     * @param voucherId
     * @return
     */
    Result createVoucherOrder(Long voucherId);

    /**
     * 基于Redisson实现分布式锁解决库存超卖问题
     *
     * @param voucherId
     * @return
     */
    Result seckillVoucherWithRedisson(Long voucherId);

    /**
     * 优惠券秒杀优化
     *
     * @param voucherId
     * @return
     */
    Result seckillVoucherWithRedissonOptimization(Long voucherId);

}
