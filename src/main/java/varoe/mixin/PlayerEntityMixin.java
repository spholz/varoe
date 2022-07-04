// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import varoe.Varoe;
import varoe.VaroeData;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
    @Shadow
    @Final
    private GameProfile gameProfile;

    @Redirect(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isAttackable()Z"))
    private boolean isAttackable(Entity target) {
        if (Varoe.getInstance().getRegisteredPlayers().containsKey(gameProfile) && Varoe.getInstance().getGameState() != VaroeData.GameState.STARTED)
            return false;

        return target.isAttackable();
    }
}
