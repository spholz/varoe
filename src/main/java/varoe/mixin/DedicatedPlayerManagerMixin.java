// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.dedicated.DedicatedPlayerManager;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import varoe.Varoe;
import varoe.VaroeData;

@Mixin(DedicatedPlayerManager.class)
public class DedicatedPlayerManagerMixin {
    private static final Logger LOGGER = Varoe.getLogger();

    @Inject(at = @At("HEAD"), method = "isWhitelisted", cancellable = true)
    private void isWhitelisted(GameProfile profile, CallbackInfoReturnable<Boolean> cir) {
        var registeredPlayers = Varoe.getInstance().getRegisteredPlayers();

        if (registeredPlayers == null) {
            // this happens when a player joins while the server is still starting
            cir.setReturnValue(false);
            return;
        }

        var player = registeredPlayers.values().stream().filter(p -> p.getProfile().equals(profile)).findAny();

        if (Varoe.getInstance().getGameState() != VaroeData.GameState.NOT_STARTED && player.isPresent() && player.get().isAlive()) {
            LOGGER.info("VaroPlayer \"{}\" joined", player.get().getProfile().getName());
            cir.setReturnValue(true);
        } else if (player.isPresent() && !player.get().isAlive()) {
            // TODO this is printed when a registered dead op tries to join
            LOGGER.info("Dead VaroPlayer \"{}\" tried to join", player.get().getProfile().getName());
        }
        // TODO change injection point to PlayerManager.checkCanJoin() to use a custom message when a player is dead
    }
}
