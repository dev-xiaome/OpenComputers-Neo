package li.cil.oc.common.entity;

import li.cil.oc.OpenComputers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class EntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, OpenComputers.ID());

    public static final RegistryObject<EntityType<Drone>> DRONE =
            ENTITY_TYPES.register("drone", () -> EntityType.Builder.of(Drone::new, MobCategory.MISC)
                    .sized(12 / 16f, 6 / 16f).fireImmune().build("drone"));

    private EntityTypes() {
        throw new Error();
    }
}