package li.cil.oc.integration.create;

import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import net.minecraft.world.item.ItemStack;

public final class CreatePackageEnvironments {
    private CreatePackageEnvironments() {
    }

    public static final class Frogport extends CreateEnvironment<FrogportBlockEntity> {
        Frogport(final FrogportBlockEntity blockEntity) {
            super(blockEntity, "Create_Frogport");
        }

        @Callback
        public Object[] setAddress(final Context context, final Arguments args) {
            blockEntity.addressFilter = args.checkString(0);
            blockEntity.filterChanged();
            blockEntity.notifyUpdate();
            return result();
        }

        @Callback
        public Object[] getAddress(final Context context, final Arguments args) {
            return result(blockEntity.addressFilter);
        }

        @Callback
        public Object[] getConfiguration(final Context context, final Arguments args) {
            return result(blockEntity.target == null ? null : blockEntity.acceptsPackages ? "send_recieve" : "send");
        }

        @Callback
        public Object[] setConfiguration(final Context context, final Arguments args) {
            return result(setConfiguration(args.checkString(0)));
        }

        private boolean setConfiguration(final String configuration) {
            if (blockEntity.target == null)
                return false;
            if ("send_recieve".equals(configuration))
                blockEntity.acceptsPackages = true;
            else if ("send".equals(configuration))
                blockEntity.acceptsPackages = false;
            else
                throw new IllegalArgumentException("Unknown configuration: \"" + configuration
                        + "\". Possible configurations are: \"send_recieve\" and \"send\".");
            blockEntity.filterChanged();
            blockEntity.notifyUpdate();
            return true;
        }

        @Callback
        public Object[] list(final Context context, final Arguments args) {
            return result(CreateLuaConversion.list(blockEntity.inventory));
        }

        @Callback
        public Object[] getItemDetail(final Context context, final Arguments args) {
            return result(CreateLuaConversion.getItemDetail(blockEntity.inventory, args.checkInteger(0)));
        }
    }

    public static final class Postbox extends CreateEnvironment<PostboxBlockEntity> {
        Postbox(final PostboxBlockEntity blockEntity) {
            super(blockEntity, "Create_Postbox");
        }

        @Callback
        public Object[] setAddress(final Context context, final Arguments args) {
            blockEntity.addressFilter = args.checkString(0);
            blockEntity.filterChanged();
            blockEntity.notifyUpdate();
            return result();
        }

        @Callback
        public Object[] getAddress(final Context context, final Arguments args) {
            return result(blockEntity.addressFilter);
        }

        @Callback
        public Object[] getConfiguration(final Context context, final Arguments args) {
            return result(blockEntity.target == null ? null : blockEntity.acceptsPackages ? "send_recieve" : "send");
        }

        @Callback
        public Object[] setConfiguration(final Context context, final Arguments args) {
            final String configuration = args.checkString(0);
            if (blockEntity.target == null)
                return result(false);
            if ("send_recieve".equals(configuration))
                blockEntity.acceptsPackages = true;
            else if ("send".equals(configuration))
                blockEntity.acceptsPackages = false;
            else
                throw new IllegalArgumentException("Unknown configuration: \"" + configuration
                        + "\". Possible configurations are: \"send_recieve\" and \"send\".");
            blockEntity.filterChanged();
            blockEntity.notifyUpdate();
            return result(true);
        }

        @Callback
        public Object[] list(final Context context, final Arguments args) {
            return result(CreateLuaConversion.list(blockEntity.inventory));
        }

        @Callback
        public Object[] getItemDetail(final Context context, final Arguments args) {
            return result(CreateLuaConversion.getItemDetail(blockEntity.inventory, args.checkInteger(0)));
        }
    }

    public abstract static class PackagerBase<T extends PackagerBlockEntity> extends CreateEnvironment<T> {
        PackagerBase(final T blockEntity, final String name) {
            super(blockEntity, name);
        }

        @Override
        protected void onFirstComputerAttach() {
            blockEntity.hasCustomComputerAddress = false;
        }

        @Override
        protected void onLastComputerDetach() {
            blockEntity.hasCustomComputerAddress = false;
        }

        @Callback
        public Object[] makePackage(final Context context, final Arguments args) {
            if (!blockEntity.heldBox.isEmpty())
                return result(false);
            blockEntity.activate();
            return result(!blockEntity.heldBox.isEmpty());
        }

        @Callback
        public Object[] list(final Context context, final Arguments args) {
            return result(CreateLuaConversion.list(blockEntity.targetInventory.getInventory()));
        }

        @Callback
        public Object[] getItemDetail(final Context context, final Arguments args) {
            return result(CreateLuaConversion.getItemDetail(blockEntity.targetInventory.getInventory(), args.checkInteger(0)));
        }

        @Callback
        public Object[] getAddress(final Context context, final Arguments args) {
            blockEntity.updateSignAddress();
            return result(blockEntity.signBasedAddress);
        }

        @Callback
        public Object[] setAddress(final Context context, final Arguments args) {
            if (args.count() > 0 && args.checkAny(0) != null) {
                final String address = args.checkString(0);
                blockEntity.customComputerAddress = address;
                blockEntity.signBasedAddress = address;
                blockEntity.hasCustomComputerAddress = true;
            } else {
                blockEntity.customComputerAddress = "";
                blockEntity.hasCustomComputerAddress = false;
            }
            return result();
        }

        @Callback
        public Object[] getPackage(final Context context, final Arguments args) {
            final ItemStack box = blockEntity.heldBox;
            return result(box.isEmpty() ? null : new CreatePackageValue(blockEntity, box));
        }
    }

    public static final class Packager extends PackagerBase<PackagerBlockEntity> {
        Packager(final PackagerBlockEntity blockEntity) {
            super(blockEntity, "Create_Packager");
        }
    }

    public static final class Repackager extends PackagerBase<RepackagerBlockEntity> {
        Repackager(final RepackagerBlockEntity blockEntity) {
            super(blockEntity, "Create_Repackager");
        }
    }
}
