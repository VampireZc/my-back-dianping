package com.zc.mybackdianping.utils.inter;

public interface RedisLock {

    boolean tryLock(Long timeout);

    void unlock();
}
