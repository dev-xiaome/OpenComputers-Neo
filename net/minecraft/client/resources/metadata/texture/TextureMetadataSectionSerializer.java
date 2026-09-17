package net.minecraft.client.resources.metadata.texture;

import com.google.gson.JsonObject;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.util.GsonHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TextureMetadataSectionSerializer implements MetadataSectionSerializer<TextureMetadataSection> {
    public TextureMetadataSection fromJson(JsonObject json) {
        boolean flag = GsonHelper.getAsBoolean(json, "blur", false);
        boolean flag1 = GsonHelper.getAsBoolean(json, "clamp", false);
        return new TextureMetadataSection(flag, flag1);
    }

    @Override
    public String getMetadataSectionName() {
        return "texture";
    }
}
