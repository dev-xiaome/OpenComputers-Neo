package net.minecraft.server.level;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;

public record ClientInformation(
    String language,
    int viewDistance,
    ChatVisiblity chatVisibility,
    boolean chatColors,
    int modelCustomisation,
    HumanoidArm mainHand,
    boolean textFilteringEnabled,
    boolean allowsListing
) {
    public static final int MAX_LANGUAGE_LENGTH = 16;

    public ClientInformation(FriendlyByteBuf p_302026_) {
        this(
            p_302026_.readUtf(16),
            p_302026_.readByte(),
            p_302026_.readEnum(ChatVisiblity.class),
            p_302026_.readBoolean(),
            p_302026_.readUnsignedByte(),
            p_302026_.readEnum(HumanoidArm.class),
            p_302026_.readBoolean(),
            p_302026_.readBoolean()
        );
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.language);
        buffer.writeByte(this.viewDistance);
        buffer.writeEnum(this.chatVisibility);
        buffer.writeBoolean(this.chatColors);
        buffer.writeByte(this.modelCustomisation);
        buffer.writeEnum(this.mainHand);
        buffer.writeBoolean(this.textFilteringEnabled);
        buffer.writeBoolean(this.allowsListing);
    }

    public static ClientInformation createDefault() {
        return new ClientInformation("en_us", 2, ChatVisiblity.FULL, true, 0, Player.DEFAULT_MAIN_HAND, false, false);
    }
}
