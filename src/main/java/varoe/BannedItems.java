// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe;

import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtil;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;

public class BannedItems {
    public final ArrayList<Identifier> bannedItems;
    public final ArrayList<Identifier> bannedPotions;

    public BannedItems() {
        bannedItems = new ArrayList<>();
        bannedPotions = new ArrayList<>();

        // bannedItems.add(Registry.ITEM.getId(Items.DIAMOND_BLOCK));
        // bannedPotions.add(Registry.POTION.getId(Potions.AWKWARD));
    }

    public boolean isBanned(ItemStack stack) {
        return bannedItems.contains(Registry.ITEM.getId(stack.getItem())) || bannedPotions.contains(Registry.POTION.getId(PotionUtil.getPotion(stack)));
    }
}
