package net.minecraft.client.renderer.texture.atlas.sources;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DirectoryLister implements SpriteSource {
    public static final MapCodec<DirectoryLister> CODEC = RecordCodecBuilder.mapCodec(
        p_262096_ -> p_262096_.group(
                    Codec.STRING.fieldOf("source").forGetter(p_261592_ -> p_261592_.sourcePath),
                    Codec.STRING.fieldOf("prefix").forGetter(p_262146_ -> p_262146_.idPrefix)
                )
                .apply(p_262096_, DirectoryLister::new)
    );
    private final String sourcePath;
    private final String idPrefix;

    public DirectoryLister(String sourcePath, String idPrefix) {
        this.sourcePath = sourcePath;
        this.idPrefix = idPrefix;
    }

    @Override
    public void run(ResourceManager resourceManager, SpriteSource.Output output) {
        FileToIdConverter filetoidconverter = new FileToIdConverter("textures/" + this.sourcePath, ".png");
        filetoidconverter.listMatchingResources(resourceManager).forEach((p_261906_, p_261635_) -> {
            ResourceLocation resourcelocation = filetoidconverter.fileToId(p_261906_).withPrefix(this.idPrefix);
            output.add(resourcelocation, p_261635_);
        });
    }

    @Override
    public SpriteSourceType type() {
        return SpriteSources.DIRECTORY;
    }
}
