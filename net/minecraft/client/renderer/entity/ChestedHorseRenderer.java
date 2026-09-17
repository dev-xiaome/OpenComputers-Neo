package net.minecraft.client.renderer.entity;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.client.model.ChestedHorseModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ChestedHorseRenderer<T extends AbstractChestedHorse> extends AbstractHorseRenderer<T, ChestedHorseModel<T>> {
    private static final Map<EntityType<?>, ResourceLocation> MAP = Maps.newHashMap(
        ImmutableMap.of(
            EntityType.DONKEY,
            ResourceLocation.withDefaultNamespace("textures/entity/horse/donkey.png"),
            EntityType.MULE,
            ResourceLocation.withDefaultNamespace("textures/entity/horse/mule.png")
        )
    );

    public ChestedHorseRenderer(EntityRendererProvider.Context context, float scale, ModelLayerLocation layer) {
        super(context, new ChestedHorseModel<>(context.bakeLayer(layer)), scale);
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(T entity) {
        return MAP.get(entity.getType());
    }
}
