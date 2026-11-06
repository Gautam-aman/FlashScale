
-- KEYS[1]: pending sorted set
-- KEYS[2]: pending quantities hash
-- ARGV[1]: hold ID

local holdId = ARGV[1]

local removed = redis.call(
    'HDEL',
    KEYS[2],
    holdId
)

redis.call(
    'ZREM',
    KEYS[1],
    holdId
)

-- Do not refund stock:
-- PostgreSQL has committed the reservation.
return removed

