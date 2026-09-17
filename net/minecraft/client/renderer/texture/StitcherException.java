package net.minecraft.client.renderer.texture;

import java.util.Collection;
import java.util.Locale;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class StitcherException extends RuntimeException {
    private final Collection<Stitcher.Entry> allSprites;

    public StitcherException(Stitcher.Entry entry, Collection<Stitcher.Entry> allSprites) {
        super(
            String.format(
                Locale.ROOT,
                "Unable to fit: %s - size: %dx%d - Maybe try a lower resolution resourcepack?",
                entry.name(),
                entry.width(),
                entry.height()
            )
        );
        this.allSprites = allSprites;
    }

    public Collection<Stitcher.Entry> getAllSprites() {
        return this.allSprites;
    }
}
