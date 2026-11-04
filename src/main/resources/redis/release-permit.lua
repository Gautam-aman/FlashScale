
-- KEYS[1]: item permit sorted-set key
-- ARGV[1]: permit ID to release

return redis.call(
    'ZREM',
    KEYS[1],
    ARGV[1]
)
