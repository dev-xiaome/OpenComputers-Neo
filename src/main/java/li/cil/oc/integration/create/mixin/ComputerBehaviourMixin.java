package li.cil.oc.integration.create.mixin;

import com.simibubi.create.compat.computercraft.events.ComputerEvent;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import li.cil.oc.integration.create.CreateComputerBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.compat.computercraft.implementation.ComputerBehaviour", remap = false)
abstract class ComputerBehaviourMixin {
    @Inject(method = "prepareComputerEvent", at = @At("HEAD"))
    private void opencomputers$forwardEvent(final ComputerEvent event, final CallbackInfo callback) {
        CreateComputerBridge.forwardEvent(((BlockEntityBehaviour) (Object) this).blockEntity, event);
    }
}
