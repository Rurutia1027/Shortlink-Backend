package org.tus.shortlink.sharding.integration.svc;

import org.springframework.stereotype.Service;
import org.tus.shortlink.sharding.ShardContextHolder;
import org.tus.shortlink.sharding.UseShard;

@Service
public class ShardingTestService {

    @UseShard
    public String execute(String gid) {
        return ShardContextHolder.getShard();
    }
}