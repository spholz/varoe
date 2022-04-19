// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import varoe.Varoe;

import java.util.function.BooleanSupplier;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Shadow
    @Nullable
    public static Inventory getInventoryAt(World world, BlockPos pos) {
        return null;
    }

    @Inject(method = "insertAndExtract", at = @At("HEAD"), cancellable = true)
    private static void insertAndExtract(World world, BlockPos pos, BlockState state, HopperBlockEntity blockEntity, BooleanSupplier booleanSupplier, CallbackInfoReturnable<Boolean> cir) {
        BlockPos sourcePos = new BlockPos(pos.getX(), pos.getY() + 1.0D, pos.getZ());
        var inv = getInventoryAt(world, sourcePos);

        if (inv instanceof ChestBlockEntity) {
            var team = Varoe.getInstance().getTeamChests().get(sourcePos);

            if (team != null)
                cir.setReturnValue(false);
        }
    }
}
