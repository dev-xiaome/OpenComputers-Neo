package li.cil.oc.integration.create.mixin;

import com.simibubi.create.compat.computercraft.events.ComputerEvent;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import li.cil.oc.integration.create.CreateComputerBridge;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.simibubi.create.compat.computercraft.AbstractComputerBehaviour", remap = false)
abstract class AbstractComputerBehaviourMixin {
    @Inject(method = "hasAttachedComputer", at = @At("RETURN"), cancellable = true)
    private void opencomputers$includeOpenComputers(final CallbackInfoReturnable<Boolean> callback) {
        final BlockEntityBehaviour self = (BlockEntityBehaviour) (Object) this;
        callback.setReturnValue(callback.getReturnValue() || CreateComputerBridge.isAttached(self.blockEntity));
    }

    @Inject(method = "prepareComputerEvent", at = @At("HEAD"))
    private void opencomputers$forwardEvent(final ComputerEvent event, final CallbackInfo callback) {
        final BlockEntityBehaviour self = (BlockEntityBehaviour) (Object) this;
        CreateComputerBridge.forwardEvent(self.blockEntity, event);
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void opencomputers$writeAttached(final CompoundTag tag, final HolderLookup.Provider registries,
                                             final boolean clientPacket, final CallbackInfo callback) {
        final BlockEntityBehaviour self = (BlockEntityBehaviour) (Object) this;
        tag.putBoolean(CreateComputerBridge.ATTACHED_TAG, CreateComputerBridge.isAttached(self.blockEntity));
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void opencomputers$readAttached(final CompoundTag tag, final HolderLookup.Provider registries,
                                            final boolean clientPacket, final CallbackInfo callback) {
        final BlockEntityBehaviour self = (BlockEntityBehaviour) (Object) this;
        if (self.blockEntity.getLevel() != null && self.blockEntity.getLevel().isClientSide)
            CreateComputerBridge.setClientAttached(self.blockEntity,
                    tag.getBoolean(CreateComputerBridge.ATTACHED_TAG));
    }
}
