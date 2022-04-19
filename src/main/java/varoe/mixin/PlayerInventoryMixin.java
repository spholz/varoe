// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe.mixin;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import varoe.Varoe;

@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {
    @Inject(at = @At("HEAD"), method = "setStack", cancellable = true)
    private void setStack(int slot, ItemStack stack, CallbackInfo ci) {
        if (Varoe.getInstance().getBannedItems().isBanned(stack))
            ci.cancel();
    }

    @Inject(at = @At("HEAD"), method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", cancellable = true)
    private void insertStack(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (Varoe.getInstance().getBannedItems().isBanned(stack))
            cir.setReturnValue(false);
    }
}
