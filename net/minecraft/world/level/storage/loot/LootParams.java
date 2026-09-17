package net.minecraft.world.level.storage.loot;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;

public class LootParams {
    private final ServerLevel level;
    private final Map<LootContextParam<?>, Object> params;
    private final Map<ResourceLocation, LootParams.DynamicDrop> dynamicDrops;
    private final float luck;

    public LootParams(
        ServerLevel level, Map<LootContextParam<?>, Object> params, Map<ResourceLocation, LootParams.DynamicDrop> dynamicDrops, float luck
    ) {
        this.level = level;
        this.params = params;
        this.dynamicDrops = dynamicDrops;
        this.luck = luck;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    public boolean hasParam(LootContextParam<?> param) {
        return this.params.containsKey(param);
    }

    public <T> T getParameter(LootContextParam<T> param) {
        T t = (T)this.params.get(param);
        if (t == null) {
            throw new NoSuchElementException(param.getName().toString());
        } else {
            return t;
        }
    }

    @Nullable
    public <T> T getOptionalParameter(LootContextParam<T> param) {
        return (T)this.params.get(param);
    }

    @Nullable
    public <T> T getParamOrNull(LootContextParam<T> param) {
        return (T)this.params.get(param);
    }

    public void addDynamicDrops(ResourceLocation location, Consumer<ItemStack> consumer) {
        LootParams.DynamicDrop lootparams$dynamicdrop = this.dynamicDrops.get(location);
        if (lootparams$dynamicdrop != null) {
            lootparams$dynamicdrop.add(consumer);
        }
    }

    public float getLuck() {
        return this.luck;
    }

    public static class Builder {
        private final ServerLevel level;
        private final Map<LootContextParam<?>, Object> params = Maps.newIdentityHashMap();
        private final Map<ResourceLocation, LootParams.DynamicDrop> dynamicDrops = Maps.newHashMap();
        private float luck;

        public Builder(ServerLevel level) {
            this.level = level;
        }

        public ServerLevel getLevel() {
            return this.level;
        }

        public <T> LootParams.Builder withParameter(LootContextParam<T> parameter, T value) {
            this.params.put(parameter, value);
            return this;
        }

        public <T> LootParams.Builder withOptionalParameter(LootContextParam<T> parameter, @Nullable T value) {
            if (value == null) {
                this.params.remove(parameter);
            } else {
                this.params.put(parameter, value);
            }

            return this;
        }

        public <T> T getParameter(LootContextParam<T> parameter) {
            T t = (T)this.params.get(parameter);
            if (t == null) {
                throw new NoSuchElementException(parameter.getName().toString());
            } else {
                return t;
            }
        }

        @Nullable
        public <T> T getOptionalParameter(LootContextParam<T> parameter) {
            return (T)this.params.get(parameter);
        }

        public LootParams.Builder withDynamicDrop(ResourceLocation name, LootParams.DynamicDrop dynamicDrop) {
            LootParams.DynamicDrop lootparams$dynamicdrop = this.dynamicDrops.put(name, dynamicDrop);
            if (lootparams$dynamicdrop != null) {
                throw new IllegalStateException("Duplicated dynamic drop '" + this.dynamicDrops + "'");
            } else {
                return this;
            }
        }

        public LootParams.Builder withLuck(float luck) {
            this.luck = luck;
            return this;
        }

        public LootParams create(LootContextParamSet params) {
            Set<LootContextParam<?>> set = Sets.difference(this.params.keySet(), params.getAllowed());
            if (false && !set.isEmpty()) { // Forge: Allow mods to pass custom loot parameters (not part of the vanilla loot table) to the loot context.
                throw new IllegalArgumentException("Parameters not allowed in this parameter set: " + set);
            } else {
                Set<LootContextParam<?>> set1 = Sets.difference(params.getRequired(), this.params.keySet());
                if (!set1.isEmpty()) {
                    throw new IllegalArgumentException("Missing required parameters: " + set1);
                } else {
                    return new LootParams(this.level, this.params, this.dynamicDrops, this.luck);
                }
            }
        }
    }

    @FunctionalInterface
    public interface DynamicDrop {
        void add(Consumer<ItemStack> output);
    }
}
