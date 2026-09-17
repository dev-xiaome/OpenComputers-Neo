package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.levelgen.GeodeBlockSettings;
import net.minecraft.world.level.levelgen.GeodeCrackSettings;
import net.minecraft.world.level.levelgen.GeodeLayerSettings;

public class GeodeConfiguration implements FeatureConfiguration {
    public static final Codec<Double> CHANCE_RANGE = Codec.doubleRange(0.0, 1.0);
    public static final Codec<GeodeConfiguration> CODEC = RecordCodecBuilder.create(
        p_160842_ -> p_160842_.group(
                    GeodeBlockSettings.CODEC.fieldOf("blocks").forGetter(p_160868_ -> p_160868_.geodeBlockSettings),
                    GeodeLayerSettings.CODEC.fieldOf("layers").forGetter(p_160866_ -> p_160866_.geodeLayerSettings),
                    GeodeCrackSettings.CODEC.fieldOf("crack").forGetter(p_160864_ -> p_160864_.geodeCrackSettings),
                    CHANCE_RANGE.fieldOf("use_potential_placements_chance").orElse(0.35).forGetter(p_160862_ -> p_160862_.usePotentialPlacementsChance),
                    CHANCE_RANGE.fieldOf("use_alternate_layer0_chance").orElse(0.0).forGetter(p_160860_ -> p_160860_.useAlternateLayer0Chance),
                    Codec.BOOL.fieldOf("placements_require_layer0_alternate").orElse(true).forGetter(p_160858_ -> p_160858_.placementsRequireLayer0Alternate),
                    IntProvider.codec(1, 20).fieldOf("outer_wall_distance").orElse(UniformInt.of(4, 5)).forGetter(p_160856_ -> p_160856_.outerWallDistance),
                    IntProvider.codec(1, 20).fieldOf("distribution_points").orElse(UniformInt.of(3, 4)).forGetter(p_160854_ -> p_160854_.distributionPoints),
                    IntProvider.codec(0, 10).fieldOf("point_offset").orElse(UniformInt.of(1, 2)).forGetter(p_160852_ -> p_160852_.pointOffset),
                    Codec.INT.fieldOf("min_gen_offset").orElse(-16).forGetter(p_160850_ -> p_160850_.minGenOffset),
                    Codec.INT.fieldOf("max_gen_offset").orElse(16).forGetter(p_160848_ -> p_160848_.maxGenOffset),
                    CHANCE_RANGE.fieldOf("noise_multiplier").orElse(0.05).forGetter(p_160846_ -> p_160846_.noiseMultiplier),
                    Codec.INT.fieldOf("invalid_blocks_threshold").forGetter(p_160844_ -> p_160844_.invalidBlocksThreshold)
                )
                .apply(p_160842_, GeodeConfiguration::new)
    );
    public final GeodeBlockSettings geodeBlockSettings;
    public final GeodeLayerSettings geodeLayerSettings;
    public final GeodeCrackSettings geodeCrackSettings;
    public final double usePotentialPlacementsChance;
    public final double useAlternateLayer0Chance;
    public final boolean placementsRequireLayer0Alternate;
    public final IntProvider outerWallDistance;
    public final IntProvider distributionPoints;
    public final IntProvider pointOffset;
    public final int minGenOffset;
    public final int maxGenOffset;
    public final double noiseMultiplier;
    public final int invalidBlocksThreshold;

    public GeodeConfiguration(
        GeodeBlockSettings geodeBlockSettings,
        GeodeLayerSettings geodeLayerSettings,
        GeodeCrackSettings geodeCrackSettings,
        double usePotentialPlacementsChance,
        double useAlternateLayer0Chance,
        boolean placementsRequireLayer0Alternate,
        IntProvider outerWallDistance,
        IntProvider distributionPoints,
        IntProvider pointOffset,
        int minGenOffset,
        int maxGenOffset,
        double noiseMultiplier,
        int invalidBlocksThreshold
    ) {
        this.geodeBlockSettings = geodeBlockSettings;
        this.geodeLayerSettings = geodeLayerSettings;
        this.geodeCrackSettings = geodeCrackSettings;
        this.usePotentialPlacementsChance = usePotentialPlacementsChance;
        this.useAlternateLayer0Chance = useAlternateLayer0Chance;
        this.placementsRequireLayer0Alternate = placementsRequireLayer0Alternate;
        this.outerWallDistance = outerWallDistance;
        this.distributionPoints = distributionPoints;
        this.pointOffset = pointOffset;
        this.minGenOffset = minGenOffset;
        this.maxGenOffset = maxGenOffset;
        this.noiseMultiplier = noiseMultiplier;
        this.invalidBlocksThreshold = invalidBlocksThreshold;
    }
}
