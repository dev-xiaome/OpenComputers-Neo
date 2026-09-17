package net.minecraft.data.info;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.RegistryOps;

public class ItemListReport implements DataProvider {
    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public ItemListReport(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this.output = output;
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        Path path = this.output.getOutputFolder(PackOutput.Target.REPORTS).resolve("items.json");
        return this.registries
            .thenCompose(
                p_330267_ -> {
                    JsonObject jsonobject = new JsonObject();
                    RegistryOps<JsonElement> registryops = p_330267_.createSerializationContext(JsonOps.INSTANCE);
                    p_330267_.lookupOrThrow(Registries.ITEM)
                        .listElements()
                        .forEach(
                            p_347011_ -> {
                                JsonObject jsonobject1 = new JsonObject();
                                jsonobject1.add(
                                    "components",
                                    DataComponentMap.CODEC
                                        .encodeStart(registryops, p_347011_.value().components())
                                        .getOrThrow(p_347012_ -> new IllegalStateException("Failed to encode components: " + p_347012_))
                                );
                                jsonobject.add(p_347011_.getRegisteredName(), jsonobject1);
                            }
                        );
                    return DataProvider.saveStable(output, jsonobject, path);
                }
            );
    }

    @Override
    public final String getName() {
        return "Item List";
    }
}
