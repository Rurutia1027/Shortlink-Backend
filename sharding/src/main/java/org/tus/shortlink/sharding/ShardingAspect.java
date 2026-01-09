package org.tus.shortlink.sharding;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ShardingAspect {

    private final ShardKeyStrategy shardKeyStrategy;

    public ShardingAspect(ShardKeyStrategy shardKeyStrategy) {
        this.shardKeyStrategy = shardKeyStrategy;
    }

    @Before("@annotation(org.tus.shortlink.sharding.UseShard) && args(gid,..)")
    public void routeShard(String gid) {
        String shard = "ds_" + (Math.abs(gid.hashCode()) % 2);
        ShardContextHolder.setShard(shard);
    }

    @After("@annotation(org.tus.shortlink.sharding.UseShard)")
    public void clearShard() {
        ShardContextHolder.clear();
    }
}