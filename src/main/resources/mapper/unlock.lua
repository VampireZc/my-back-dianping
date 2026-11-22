-- 比较当前线程标识与目标的线程是否一致
if (redis.call('get',KEYS[1]) == ARGV[1]) then
    -- 如果一致则删除锁
    return redis.call('del',KEYS[1])
end
return 0
