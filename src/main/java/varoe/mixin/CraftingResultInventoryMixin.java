// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe.mixin;

import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import varoe.Varoe;

@Mixin(CraftingResultInventory.class)
public class CraftingResultInventoryMixin {
    @Inject(at = @At("HEAD"), method = "setStack", cancellable = true)
    private void setStack(int slot, ItemStack stack, CallbackInfo ci) {
        if (Varoe.getInstance().getBannedItems().isBanned(stack))
            ci.cancel();
    }
}
