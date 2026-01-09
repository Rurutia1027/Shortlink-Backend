package org.tus.shortlink.sharding;

public class ShardContextHolder {
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<String>();

    public static void setShard(String shardKey) {
        CONTEXT.set(shardKey);
    }

    public static String getShared() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
