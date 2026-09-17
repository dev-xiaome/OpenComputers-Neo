package net.minecraft.client.renderer.entity;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class UndeadHorseRenderer extends AbstractHorseRenderer<AbstractHorse, HorseModel<AbstractHorse>> {
    private static final Map<EntityType<?>, ResourceLocation> MAP = Maps.newHashMap(
        ImmutableMap.of(
            EntityType.ZOMBIE_HORSE,
            ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_zombie.png"),
            EntityType.SKELETON_HORSE,
            ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_skeleton.png")
        )
    );

    public UndeadHorseRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer) {
        super(context, new HorseModel<>(context.bakeLayer(layer)), 1.0F);
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(AbstractHorse entity) {
        return MAP.get(entity.getType());
    }
}
