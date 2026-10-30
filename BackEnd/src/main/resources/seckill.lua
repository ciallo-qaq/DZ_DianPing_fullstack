-- 秒杀资格判定脚本（Redis + Lua）：库存判断、一人一单、预扣库存、用户占位在一次原子调用内完成
-- KEYS     : 无。key 由脚本内部用固定前缀拼接，避免从外部传入造成 key 拼接错误
-- ARGV[1]  : voucherId  优惠券 id
-- ARGV[2]  : userId     用户 id
-- 返回      : 0=成功占位并预扣  1=库存不足  2=重复下单  -1=库存 key 不存在（活动未初始化或已结束）
--
-- 订单落库不再写 Redis Stream：改为应用层在脚本返回 0 之后把订单消息投递到 RabbitMQ（X 交换机 / XA 路由键）。

local voucherId = ARGV[1]
local userId = ARGV[2]

-- 1.数据 key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 2.判断库存是否充足
local stock = tonumber(redis.call('get', stockKey))
if stock == nil then
    return -1
end
if stock <= 0 then
    return 1
end

-- 3.判断用户是否已经下过单（一人一单）
if redis.call('sismember', orderKey, userId) == 1 then
    return 2
end

-- 4.预扣库存 + 占位用户：两步都在脚本内，天然原子，不会出现"扣了库存没占位"的中间态
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)

-- 5.返回成功，由应用层投递订单消息到 RabbitMQ
return 0
