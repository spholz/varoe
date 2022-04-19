// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import varoe.Varoe;
import varoe.VaroeData;

import java.util.Optional;

import static net.minecraft.sound.SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin extends PlayerEntity {
    @Shadow @Final public MinecraftServer server;

    @Shadow public ServerPlayNetworkHandler networkHandler;

    public ServerPlayerEntityMixin(World world, BlockPos pos, float yaw, GameProfile profile) {
        super(world, pos, yaw, profile);
    }

    @Inject(at = @At("TAIL"), method = "onDeath")
    private void onDeath(DamageSource source, CallbackInfo ci) {
        if (Varoe.getInstance().getGameState() != VaroeData.GameState.STARTED)
            return;

        // TODO is this the right injection point? maybe put this into the respawn function so you still get the respawn screen
        var player = Optional.ofNullable(Varoe.getInstance().getRegisteredPlayers().get(getGameProfile()));
        // var player = Varoe.getInstance().getRegisteredPlayers().keys.stream().filter(p -> p.getProfile().equals(getGameProfile())).findAny();

        player.ifPresent(varoPlayer -> {
            varoPlayer.setAlive(false);

            for (var p : server.getPlayerManager().getPlayerList())
                p.playSound(ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);

            networkHandler.disconnect(new LiteralText("You died.\nGame Over").formatted(Formatting.RED));

            Varoe.getInstance().checkForVictory();
        });
    }

    @Inject(at = @At("HEAD"), method = "moveToSpawn", cancellable = true)
    private void moveToSpawn(ServerWorld world, CallbackInfo ci) {
        var player = Varoe.getInstance().getRegisteredPlayers().get(getGameProfile());

        if (player != null && player.getSpawnPos() != null) {
            setPosition(player.getSpawnPos().getX() + 0.5, player.getSpawnPos().getY(), player.getSpawnPos().getZ() + 0.5);
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "getServerGameMode", cancellable = true)
    private void getServerGameMode(@Nullable GameMode backupGameMode, CallbackInfoReturnable<GameMode> cir) {
        var player = Varoe.getInstance().getRegisteredPlayers().get(getGameProfile());

        VaroeData.GameState state = Varoe.getInstance().getGameState();

        if (player != null) {
            if (state == VaroeData.GameState.STARTED)
                cir.setReturnValue(GameMode.SURVIVAL);
            else
                cir.setReturnValue(GameMode.ADVENTURE);
        }
    }
}