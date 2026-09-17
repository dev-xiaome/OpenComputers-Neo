package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record SummonEntityEffect(HolderSet<EntityType<?>> entityTypes, boolean joinTeam) implements EnchantmentEntityEffect {
    public static final MapCodec<SummonEntityEffect> CODEC = RecordCodecBuilder.mapCodec(
        p_345616_ -> p_345616_.group(
                    RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE).fieldOf("entity").forGetter(SummonEntityEffect::entityTypes),
                    Codec.BOOL.optionalFieldOf("join_team", Boolean.valueOf(false)).forGetter(SummonEntityEffect::joinTeam)
                )
                .apply(p_345616_, SummonEntityEffect::new)
    );

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity p_entity, Vec3 origin) {
        BlockPos blockpos = BlockPos.containing(origin);
        if (Level.isInSpawnableBounds(blockpos)) {
            Optional<Holder<EntityType<?>>> optional = this.entityTypes().getRandomElement(level.getRandom());
            if (!optional.isEmpty()) {
                Entity entity = optional.get().value().spawn(level, blockpos, MobSpawnType.TRIGGERED);
                if (entity != null) {
                    if (entity instanceof LightningBolt lightningbolt && item.owner() instanceof ServerPlayer serverplayer) {
                        lightningbolt.setCause(serverplayer);
                    }

                    if (this.joinTeam && p_entity.getTeam() != null) {
                        level.getScoreboard().addPlayerToTeam(entity.getScoreboardName(), p_entity.getTeam());
                    }

                    entity.moveTo(origin.x, origin.y, origin.z, entity.getYRot(), entity.getXRot());
                }
            }
        }
    }

    @Override
    public MapCodec<SummonEntityEffect> codec() {
        return CODEC;
    }
}
