package net.minecraft.world.entity.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class VillagerData {
    public static final int MIN_VILLAGER_LEVEL = 1;
    public static final int MAX_VILLAGER_LEVEL = 5;
    private static final int[] NEXT_LEVEL_XP_THRESHOLDS = new int[]{0, 10, 70, 150, 250};
    public static final Codec<VillagerData> CODEC = RecordCodecBuilder.create(
        p_344353_ -> p_344353_.group(
                    BuiltInRegistries.VILLAGER_TYPE.byNameCodec().fieldOf("type").orElseGet(() -> VillagerType.PLAINS).forGetter(p_150024_ -> p_150024_.type),
                    BuiltInRegistries.VILLAGER_PROFESSION
                        .byNameCodec()
                        .fieldOf("profession")
                        .orElseGet(() -> VillagerProfession.NONE)
                        .forGetter(p_150022_ -> p_150022_.profession),
                    Codec.INT.fieldOf("level").orElse(1).forGetter(p_150020_ -> p_150020_.level)
                )
                .apply(p_344353_, VillagerData::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerData> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.registry(Registries.VILLAGER_TYPE),
        p_319708_ -> p_319708_.type,
        ByteBufCodecs.registry(Registries.VILLAGER_PROFESSION),
        p_319709_ -> p_319709_.profession,
        ByteBufCodecs.VAR_INT,
        p_319710_ -> p_319710_.level,
        VillagerData::new
    );
    private final VillagerType type;
    private final VillagerProfession profession;
    private final int level;

    public VillagerData(VillagerType type, VillagerProfession profession, int level) {
        this.type = type;
        this.profession = profession;
        this.level = Math.max(1, level);
    }

    public VillagerType getType() {
        return this.type;
    }

    public VillagerProfession getProfession() {
        return this.profession;
    }

    public int getLevel() {
        return this.level;
    }

    public VillagerData setType(VillagerType type) {
        return new VillagerData(type, this.profession, this.level);
    }

    public VillagerData setProfession(VillagerProfession profession) {
        return new VillagerData(this.type, profession, this.level);
    }

    public VillagerData setLevel(int level) {
        return new VillagerData(this.type, this.profession, level);
    }

    public static int getMinXpPerLevel(int level) {
        return canLevelUp(level) ? NEXT_LEVEL_XP_THRESHOLDS[level - 1] : 0;
    }

    public static int getMaxXpPerLevel(int level) {
        return canLevelUp(level) ? NEXT_LEVEL_XP_THRESHOLDS[level] : 0;
    }

    public static boolean canLevelUp(int level) {
        return level >= 1 && level < 5;
    }
}
