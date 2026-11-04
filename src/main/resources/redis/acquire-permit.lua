
-- KEYS[1]: item permit sorted-set key
-- ARGV[1]: unique permit ID
-- ARGV[2]: maximum in-flight permits
-- ARGV[3]: permit lifetime in milliseconds

local permitId = ARGV[1]
local maxInFlight = tonumber(ARGV[2])
local leaseMs = tonumber(ARGV[3])

local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000
    + math.floor(tonumber(time[2]) / 1000)

-- Remove permits whose leases have expired.
redis.call(
    'ZREMRANGEBYSCORE',
    KEYS[1],
    '-inf',
    now
)

local active = redis.call('ZCARD', KEYS[1])

if active >= maxInFlight then
    return 0
end

redis.call(
    'ZADD',
    KEYS[1],
    'NX',
    now + leaseMs,
    permitId
)

-- Prevent abandoned permit keys from accumulating.
redis.call('PEXPIRE', KEYS[1], leaseMs + 1000)

return 1
