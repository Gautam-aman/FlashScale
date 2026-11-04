
-- KEYS[1]: rate-limit bucket key
-- ARGV[1]: bucket capacity
-- ARGV[2]: refill tokens per second

local capacity = tonumber(ARGV[1])
local refill = tonumber(ARGV[2])

local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000
    + math.floor(tonumber(time[2]) / 1000)

local values = redis.call(
    'HMGET', KEYS[1], 'tokens', 'updated'
)

local tokens = tonumber(values[1]) or capacity
local updated = tonumber(values[2]) or now

local elapsed = math.max(0, now - updated)

tokens = math.min(
    capacity,
    tokens + elapsed * refill / 1000
)

local allowed = 0

if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
end

redis.call(
    'HSET',
    KEYS[1],
    'tokens', tostring(tokens),
    'updated', tostring(now)
)

local ttl = math.max(
    1000,
    math.ceil(capacity / refill * 2000)
)

redis.call('PEXPIRE', KEYS[1], ttl)

return allowed
