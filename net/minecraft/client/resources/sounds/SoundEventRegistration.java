package net.minecraft.client.resources.sounds;

import java.util.List;
import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SoundEventRegistration {
    private final List<Sound> sounds;
    /**
     * if true it will override all the sounds from the resourcepacks loaded before
     */
    private final boolean replace;
    @Nullable
    private final String subtitle;

    public SoundEventRegistration(List<Sound> sounds, boolean replace, @Nullable String subtitle) {
        this.sounds = sounds;
        this.replace = replace;
        this.subtitle = subtitle;
    }

    public List<Sound> getSounds() {
        return this.sounds;
    }

    public boolean isReplace() {
        return this.replace;
    }

    @Nullable
    public String getSubtitle() {
        return this.subtitle;
    }
}
