package net.minecraft.client.gui.screens.inventory;

import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.world.entity.vehicle.MinecartCommandBlock;
import net.minecraft.world.level.BaseCommandBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MinecartCommandBlockEditScreen extends AbstractCommandBlockEditScreen {
    private final BaseCommandBlock commandBlock;

    public MinecartCommandBlockEditScreen(BaseCommandBlock commandBlock) {
        this.commandBlock = commandBlock;
    }

    @Override
    public BaseCommandBlock getCommandBlock() {
        return this.commandBlock;
    }

    @Override
    int getPreviousY() {
        return 150;
    }

    @Override
    protected void init() {
        super.init();
        this.commandEdit.setValue(this.getCommandBlock().getCommand());
    }

    @Override
    protected void populateAndSendPacket(BaseCommandBlock commandBlock) {
        if (commandBlock instanceof MinecartCommandBlock.MinecartCommandBase minecartcommandblock$minecartcommandbase) {
            this.minecraft
                .getConnection()
                .send(
                    new ServerboundSetCommandMinecartPacket(
                        minecartcommandblock$minecartcommandbase.getMinecart().getId(), this.commandEdit.getValue(), commandBlock.isTrackOutput()
                    )
                );
        }
    }
}
