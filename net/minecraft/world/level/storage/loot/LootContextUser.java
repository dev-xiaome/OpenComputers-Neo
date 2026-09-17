package net.minecraft.world.level.storage.loot;

import java.util.Set;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;

/**
 * An object that will use some parameters from a LootContext. Used for validation purposes to validate that the correct parameters are present.
 */
public interface LootContextUser {
    default Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of();
    }

    /**
     * Validate that this object is used correctly according to the given ValidationContext.
     */
    default void validate(ValidationContext context) {
        context.validateUser(this);
    }
}
