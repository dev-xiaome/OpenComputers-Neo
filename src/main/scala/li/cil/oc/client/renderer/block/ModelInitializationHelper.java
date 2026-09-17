package li.cil.oc.client.renderer.block;

import net.minecraft.world.level.block.state.properties.Property;

public class ModelInitializationHelper {
    public static String getPropertyName(Property<?> prop, Comparable<?> value) {
        return helperGetName(prop, value);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String helperGetName(Property<T> prop, Comparable<?> value) {
        return prop.getName((T) value);
    }
}