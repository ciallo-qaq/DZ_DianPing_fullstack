-- 秒杀预扣回滚脚本（幂等）：把"消息最终没能进入队列"的订单资格还给用户
-- KEYS[1] : 库存 key             seckill:stock:{voucherId}
-- KEYS[2] : 已下单用户集合 key    seckill:order:{voucherId}
-- KEYS[3] : 回滚标记 key          seckill:rollback:{orderId}
-- ARGV[1] : userId
-- ARGV[2] : 回滚标记存活时间（毫秒）
-- 返回    : 1=本次真正执行了回滚  0=此前已回滚过（重复补偿，直接忽略）
--
-- 幂等靠 SETNX 标记实现：同一次投递失败可能被同步 catch 与 Broker 回调各触发一次，
-- 没有标记就会把库存加回去两次。

if redis.call('setnx', KEYS[3], '1') == 0 then
    return 0
end
redis.call('pexpire', KEYS[3], ARGV[2])

-- 库存 key 可能已过期（活动结束被清理），只有存在时才回补，
-- 避免凭空造出一个值为 1 的库存 key
if redis.call('exists', KEYS[1]) == 1 then
    redis.call('incrby', KEYS[1], 1)
end
redis.call('srem', KEYS[2], ARGV[1])

return 1
