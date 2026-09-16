package li.cil.oc.integration.create;

import com.simibubi.create.content.logistics.box.PackageItem;
import li.cil.oc.api.driver.Converter;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/** Adds Create's package metadata/userdata to OC's normal item-stack conversion. */
final class CreatePackageConverter implements Converter {
    @Override
    public void convert(final Object value, final Map<Object, Object> output) {
        if (value instanceof ItemStack stack && PackageItem.isPackage(stack))
            output.put("package", new CreatePackageValue(null, stack));
    }
}
