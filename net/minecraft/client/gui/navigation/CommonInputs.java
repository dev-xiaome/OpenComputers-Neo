package net.minecraft.client.gui.navigation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CommonInputs {
    public static boolean selected(int key) {
        return key == 257 || key == 32 || key == 335;
    }
}
