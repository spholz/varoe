// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe;

import com.mojang.authlib.GameProfile;
import net.minecraft.util.math.Vec3i;

public class VaroPlayer {
    private final GameProfile profile;
    private boolean alive = true;
    private Vec3i spawnPos = null;

    public VaroPlayer(GameProfile profile) {
        this.profile = profile;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public GameProfile getProfile() {
        return profile;
    }

    public Vec3i getSpawnPos() {
        return spawnPos;
    }

    public void setSpawnPos(Vec3i spawnPos) {
        this.spawnPos = spawnPos;
    }
}
