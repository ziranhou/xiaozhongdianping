-- 判断锁中的线程标识是否和当前线程标识相同
if(redis.call('get', KEYS[1]) == ARGV[1]) then
    -- 相同，删除锁
    return redis.call('del', KEYS[1])
end
return 0