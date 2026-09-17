package net.minecraft.world.level.storage.loot.providers.score;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Set;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.scores.ScoreHolder;

/**
 * A {@link ScoreboardNameProvider} that always provides a fixed name.
 */
public record FixedScoreboardNameProvider(String name) implements ScoreboardNameProvider {
    public static final MapCodec<FixedScoreboardNameProvider> CODEC = RecordCodecBuilder.mapCodec(
        p_298540_ -> p_298540_.group(Codec.STRING.fieldOf("name").forGetter(FixedScoreboardNameProvider::name))
                .apply(p_298540_, FixedScoreboardNameProvider::new)
    );

    public static ScoreboardNameProvider forName(String name) {
        return new FixedScoreboardNameProvider(name);
    }

    @Override
    public LootScoreProviderType getType() {
        return ScoreboardNameProviders.FIXED;
    }

    @Override
    public ScoreHolder getScoreHolder(LootContext context) {
        return ScoreHolder.forNameOnly(this.name);
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return ImmutableSet.of();
    }
}
