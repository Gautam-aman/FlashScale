
-- KEYS[1]: available stock
-- KEYS[2]: pending sorted set
-- KEYS[3]: pending quantities hash
-- ARGV[1]: hold ID

local holdId = ARGV[1]

local quantity = tonumber(
    redis.call('HGET', KEYS[3], holdId)
)

if not quantity then
    return 0
end

redis.call('HDEL', KEYS[3], holdId)
redis.call('ZREM', KEYS[2], holdId)

-- An evicted inventory key must stay missing until reconciliation.
if redis.call('EXISTS', KEYS[1]) == 0 then
    return -2
end

redis.call(
    'INCRBY',
    KEYS[1],
    quantity
)

return 1
