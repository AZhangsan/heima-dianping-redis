--参数 优惠券ID
local voucherId = ARGV[1]
-- 用户ID
local userId = ARGV[2]
--
local orderId = ARGV[3]

-- Key: 用户下单set集合
local orderKey = "seckill:order:" .. voucherId
-- Key：库存数量
local stockKey = "seckill:stock:" .. voucherId
-- 判断库存是否充足
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 库存不足
    return 1
end
-- 判断用户是否下单，判断set中是否存在用户
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 用户已经抢购过了
    return 2
end
-- 扣减库存
redis.call('incrby', stockKey, '-1')
redis.call('sadd', orderKey, userId)
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0