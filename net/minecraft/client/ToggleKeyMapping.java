package net.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.BooleanSupplier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ToggleKeyMapping extends KeyMapping {
    private final BooleanSupplier needsToggle;

    public ToggleKeyMapping(String name, int keyCode, String category, BooleanSupplier needsToggle) {
        super(name, InputConstants.Type.KEYSYM, keyCode, category);
        this.needsToggle = needsToggle;
    }

    @Override
    public void setDown(boolean value) {
        if (this.needsToggle.getAsBoolean()) {
            if (value && isConflictContextAndModifierActive()) {
                super.setDown(!this.isDown());
            }
        } else {
            super.setDown(value);
        }
    }
    @Override public boolean isDown() { return this.isDown && (isConflictContextAndModifierActive() || needsToggle.getAsBoolean()); }

    protected void reset() {
        super.setDown(false);
    }
}
