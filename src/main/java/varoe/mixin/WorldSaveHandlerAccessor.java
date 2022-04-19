// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe.mixin;

import net.minecraft.world.WorldSaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

@Mixin(WorldSaveHandler.class)
public interface WorldSaveHandlerAccessor {
    @Accessor("playerDataDir")
    File getPlayerDataDir();
}
