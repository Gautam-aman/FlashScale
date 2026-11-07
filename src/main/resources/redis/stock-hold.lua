
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

if not quantity or quantity < 1 or quantity > 5 or quantity ~= math.floor(quantity) then
    return -3
end

local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000
    + math.floor(tonumber(time[2]) / 1000)

-- Redis cannot determine whether an expired hold committed in PostgreSQL.
-- Keep uncertain holds for offline reconciliation instead of refunding them.
local stock = tonumber(redis.call('GET', stockKey))
if not stock then
    return -2
end

if redis.call('ZCOUNT', pendingKey, '-inf', now) > 0 then
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
