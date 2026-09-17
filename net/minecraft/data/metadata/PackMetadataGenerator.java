package net.minecraft.data.metadata;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.DetectedVersion;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FeatureFlagsMetadataSection;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.world.flag.FeatureFlagSet;

public class PackMetadataGenerator implements DataProvider {
    private final PackOutput output;
    private final Map<String, Supplier<JsonElement>> elements = new HashMap<>();

    public PackMetadataGenerator(PackOutput output) {
        this.output = output;
    }

    public <T> PackMetadataGenerator add(MetadataSectionType<T> type, T value) {
        this.elements.put(type.getMetadataSectionName(), () -> type.toJson(value));
        return this;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        JsonObject jsonobject = new JsonObject();
        this.elements.forEach((p_249290_, p_251317_) -> jsonobject.add(p_249290_, p_251317_.get()));
        return DataProvider.saveStable(output, jsonobject, this.output.getOutputFolder().resolve("pack.mcmeta"));
    }

    @Override
    public final String getName() {
        return "Pack Metadata";
    }

    public static PackMetadataGenerator forFeaturePack(PackOutput output, Component description) {
        return new PackMetadataGenerator(output)
            .add(PackMetadataSection.TYPE, new PackMetadataSection(description, DetectedVersion.BUILT_IN.getPackVersion(PackType.SERVER_DATA), Optional.empty()));
    }

    public static PackMetadataGenerator forFeaturePack(PackOutput output, Component description, FeatureFlagSet flags) {
        return forFeaturePack(output, description).add(FeatureFlagsMetadataSection.TYPE, new FeatureFlagsMetadataSection(flags));
    }
}
