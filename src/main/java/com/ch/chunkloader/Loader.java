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
    public final String type;    // config.yml loaders 의 종류 이름
    public final int radius;     // 청크 반경 (1 = 3x3)
    public boolean active;       // 청크 티켓이 걸려 있는지

    public Loader(UUID standId, String world, int x, int y, int z, UUID owner, String type, int radius) {
        this.standId = standId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.owner = owner;
        this.type = type;
        this.radius = radius;
    }
}
