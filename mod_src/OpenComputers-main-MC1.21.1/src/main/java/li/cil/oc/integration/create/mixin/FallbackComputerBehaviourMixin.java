package li.cil.oc.integration.create.mixin;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import li.cil.oc.integration.create.CreateComputerBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.simibubi.create.compat.computercraft.FallbackComputerBehaviour", remap = false)
abstract class FallbackComputerBehaviourMixin {
    @Inject(method = "hasAttachedComputer", at = @At("RETURN"), cancellable = true)
    private void opencomputers$includeOpenComputers(final CallbackInfoReturnable<Boolean> callback) {
        final BlockEntityBehaviour self = (BlockEntityBehaviour) (Object) this;
        callback.setReturnValue(callback.getReturnValue() || CreateComputerBridge.isAttached(self.blockEntity));
    }
}
