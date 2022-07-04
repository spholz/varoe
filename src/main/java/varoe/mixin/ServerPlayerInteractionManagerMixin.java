// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe.mixin;

import net.minecraft.block.ChestBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import varoe.Varoe;

import java.util.Objects;

@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {
    @Shadow
    @Final
    protected ServerPlayerEntity player;

    @Redirect(method = "tryBreakBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;isBlockBreakingRestricted(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/GameMode;)Z"))
    private boolean tryBreakBlock(ServerPlayerEntity instance, World world, BlockPos pos, GameMode gameMode) {
        var team = Varoe.getInstance().getTeamChests().get(pos);
        if (!Objects.requireNonNull(instance.getServer()).getPlayerManager().isOperator(instance.getGameProfile()) && world.getBlockState(pos).getBlock() instanceof ChestBlock && team != null && !team.equals(instance.getScoreboardTeam())) {
            player.sendMessage(Text.of(String.format("This team chest belongs to %s", team.getName())), false);
            return true;
        }

        return player.isBlockBreakingRestricted(world, pos, gameMode);
    }
}
