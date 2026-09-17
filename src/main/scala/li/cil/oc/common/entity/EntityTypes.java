package li.cil.oc.common.entity;

import li.cil.oc.OpenComputers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class EntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, OpenComputers.ID());

    public static final DeferredHolder<EntityType<Drone>, EntityType<Drone>> DRONE =
            ENTITY_TYPES.register("drone", () -> EntityType.Builder.of(Drone::new, MobCategory.MISC)
                    .sized(12 / 16f, 6 / 16f).fireImmune().build("drone"));

    private EntityTypes() {
        throw new Error();
    }
}