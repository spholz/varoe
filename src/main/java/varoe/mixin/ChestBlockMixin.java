// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe.mixin;

import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import varoe.Varoe;

import java.util.Objects;
import java.util.function.Supplier;

@Mixin(ChestBlock.class)
public abstract class ChestBlockMixin extends AbstractChestBlock<ChestBlockEntity> {
    protected ChestBlockMixin(Settings settings, Supplier<BlockEntityType<? extends ChestBlockEntity>> blockEntityTypeSupplier) {
        super(settings, blockEntityTypeSupplier);
    }

    @Inject(at = @At("HEAD"), method = "onUse", cancellable = true)
    private void onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        var team = Varoe.getInstance().getTeamChests().get(pos);

        if (team != null && !Objects.requireNonNull(player.getServer()).getPlayerManager().isOperator(player.getGameProfile()) && !team.getPlayerList().contains(player.getName().getString())) {
            player.sendMessage(Text.of(String.format("This team chest belongs to %s", team.getName())), false);
            cir.setReturnValue(ActionResult.FAIL);
        }
    }

    @Inject(at = @At("HEAD"), method = "onPlaced")
    private void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack, CallbackInfo ci) {
        if (placer instanceof ServerPlayerEntity) {
            // getStateForNeighborUpdate sets the team before onPlace gets called so only set the team if Varoe.teamChests[pos] == null
            if (Varoe.getInstance().getTeamChests().get(pos) == null) {
                Varoe.getInstance().addTeamChest(pos, placer.getScoreboardTeam());
                ((ServerPlayerEntity) placer).sendMessage(Text.of("Team chest created"), false);
            }
        }
    }

    @Override
    public void onBroken(WorldAccess world, BlockPos pos, BlockState state) {
        Varoe.getInstance().deleteTeamChest(pos);
        super.onBroken(world, pos, state);
    }

    @Inject(at = @At(value = "RETURN", ordinal = 0), method = "getStateForNeighborUpdate")
    private void getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos, CallbackInfoReturnable<BlockState> cir) {
        Varoe varoe = Varoe.getInstance();
        varoe.addTeamChest(neighborPos, varoe.getTeamChests().get(pos));
    }

    @Override
    public float getBlastResistance() {
        return 3600000.0f;
    }
}
