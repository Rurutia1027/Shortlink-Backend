package org.tus.shortlink.sharding;

@FunctionalInterface
public interface ShardKeyStrategy {
    String determineShard(String key);
}