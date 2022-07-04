// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe.mixin;

import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Inject(method = "isInventoryEmpty", at = @At("HEAD"), cancellable = true)
    private static void isInventoryEmpty(Inventory inv, Direction facing, CallbackInfoReturnable<Boolean> cir) {
        // prevent hoppers from extracting from blocks (items are still allowed to be collected by hoppers)
        // injecting in getInputInventory doesn't work due to fabrics own extract logic
        cir.setReturnValue(true);
    }
}
