// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.world.WorldSaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerManager.class)
public interface PlayerManagerAccessor {
    @Accessor("saveHandler")
    WorldSaveHandler getSaveHandler();
}
