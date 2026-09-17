package net.minecraft.sounds;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceLocation;

public class SoundEvent {
    public static final Codec<SoundEvent> DIRECT_CODEC = RecordCodecBuilder.create(
        p_337569_ -> p_337569_.group(
                    ResourceLocation.CODEC.fieldOf("sound_id").forGetter(SoundEvent::getLocation),
                    Codec.FLOAT.lenientOptionalFieldOf("range").forGetter(SoundEvent::fixedRange)
                )
                .apply(p_337569_, SoundEvent::create)
    );
    public static final Codec<Holder<SoundEvent>> CODEC = RegistryFileCodec.create(Registries.SOUND_EVENT, DIRECT_CODEC);
    public static final StreamCodec<ByteBuf, SoundEvent> DIRECT_STREAM_CODEC = StreamCodec.composite(
        ResourceLocation.STREAM_CODEC, SoundEvent::getLocation, ByteBufCodecs.FLOAT.apply(ByteBufCodecs::optional), SoundEvent::fixedRange, SoundEvent::create
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<SoundEvent>> STREAM_CODEC = ByteBufCodecs.holder(
        Registries.SOUND_EVENT, DIRECT_STREAM_CODEC
    );
    private static final float DEFAULT_RANGE = 16.0F;
    private final ResourceLocation location;
    private final float range;
    private final boolean newSystem;

    private static SoundEvent create(ResourceLocation location, Optional<Float> range) {
        return range.<SoundEvent>map(p_263360_ -> createFixedRangeEvent(location, p_263360_)).orElseGet(() -> createVariableRangeEvent(location));
    }

    public static SoundEvent createVariableRangeEvent(ResourceLocation location) {
        return new SoundEvent(location, 16.0F, false);
    }

    public static SoundEvent createFixedRangeEvent(ResourceLocation location, float range) {
        return new SoundEvent(location, range, true);
    }

    private SoundEvent(ResourceLocation location, float range, boolean newSystem) {
        this.location = location;
        this.range = range;
        this.newSystem = newSystem;
    }

    public ResourceLocation getLocation() {
        return this.location;
    }

    public float getRange(float volume) {
        if (this.newSystem) {
            return this.range;
        } else {
            return volume > 1.0F ? 16.0F * volume : 16.0F;
        }
    }

    private Optional<Float> fixedRange() {
        return this.newSystem ? Optional.of(this.range) : Optional.empty();
    }
}
