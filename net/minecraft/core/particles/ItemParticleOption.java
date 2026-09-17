package net.minecraft.core.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public class ItemParticleOption implements ParticleOptions {
    private static final Codec<ItemStack> ITEM_CODEC = Codec.withAlternative(ItemStack.SINGLE_ITEM_CODEC, ItemStack.ITEM_NON_AIR_CODEC, ItemStack::new);
    private final ParticleType<ItemParticleOption> type;
    private final ItemStack itemStack;

    public static MapCodec<ItemParticleOption> codec(ParticleType<ItemParticleOption> particleType) {
        return ITEM_CODEC.xmap(p_123714_ -> new ItemParticleOption(particleType, p_123714_), p_123709_ -> p_123709_.itemStack).fieldOf("item");
    }

    public static StreamCodec<? super RegistryFriendlyByteBuf, ItemParticleOption> streamCodec(ParticleType<ItemParticleOption> particleType) {
        return ItemStack.STREAM_CODEC.map(p_319432_ -> new ItemParticleOption(particleType, p_319432_), p_319433_ -> p_319433_.itemStack);
    }

    public ItemParticleOption(ParticleType<ItemParticleOption> type, ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            throw new IllegalArgumentException("Empty stacks are not allowed");
        } else {
            this.type = type;
            this.itemStack = itemStack.copy(); //Forge: Fix stack updating after the fact causing particle changes.
        }
    }

    @Override
    public ParticleType<ItemParticleOption> getType() {
        return this.type;
    }

    public ItemStack getItem() {
        return this.itemStack;
    }
}
