package com.ch.chunkloader;

import java.util.UUID;

/** 설치된 청크로더 1개 */
public class Loader {
    public final UUID standId;   // 아머스탠드 UUID
    public final String world;
    public final int x;
    public final int y;
    public final int z;
    public final UUID owner;
    public boolean active;       // 청크 티켓이 걸려 있는지

    public Loader(UUID standId, String world, int x, int y, int z, UUID owner) {
        this.standId = standId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.owner = owner;
    }
}
