// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe;

import net.fabricmc.api.DedicatedServerModInitializer;

public class VaroeMod implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        Varoe varoe = new Varoe();
    }
}