
-- KEYS[1]: Redis available stock
-- KEYS[2]: sorted set of pending hold expiration times
-- KEYS[3]: hash of pending hold quantities
--
-- ARGV[1]: unique hold ID
-- ARGV[2]: quantity
-- ARGV[3]: lease lifetime, milliseconds

local stockKey = KEYS[1]
local pendingKey = KEYS[2]
local quantitiesKey = KEYS[3]

local holdId = ARGV[1]
local quantity = tonumber(ARGV[2])
local leaseMs = tonumber(ARGV[3])

if not quantity or quantity < 1 then
    return -3
end

local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000
    + math.floor(tonumber(time[2]) / 1000)

-- Reclaim expired provisional holds.
local expired = redis.call(
    'ZRANGEBYSCORE',
    pendingKey,
    '-inf',
    now,
    'LIMIT',
    0,
    500
)

for _, id in ipairs(expired) do
    local held = tonumber(
        redis.call('HGET', quantitiesKey, id)
    )

    if held then
        redis.call('INCRBY', stockKey, held)
        redis.call('HDEL', quantitiesKey, id)
    end

    redis.call('ZREM', pendingKey, id)
end

local stock = tonumber(redis.call('GET', stockKey))

-- Missing inventory requires reconciliation.
if not stock then
    return -2
end

if stock < quantity then
    return 0
end

-- Redis atomically acquires tentative inventory.
redis.call('DECRBY', stockKey, quantity)

redis.call(
    'HSET',
    quantitiesKey,
    holdId,
    quantity
)

redis.call(
    'ZADD',
    pendingKey,
    now + leaseMs,
    holdId
)

return 1
