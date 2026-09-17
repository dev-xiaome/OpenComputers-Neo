package net.minecraft.realms;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class RealmsScreen extends Screen {
    protected static final int TITLE_HEIGHT = 17;
    protected static final int EXPIRATION_NOTIFICATION_DAYS = 7;
    protected static final long SIZE_LIMIT = 5368709120L;
    protected static final int COLOR_DARK_GRAY = 5000268;
    protected static final int COLOR_MEDIUM_GRAY = 7105644;
    protected static final int COLOR_GREEN = 8388479;
    protected static final int COLOR_LINK = 3368635;
    protected static final int COLOR_LINK_HOVER = 7107012;
    protected static final int SKIN_FACE_SIZE = 32;
    private final List<RealmsLabel> labels = Lists.newArrayList();

    public RealmsScreen(Component title) {
        super(title);
    }

    protected static int row(int position) {
        return 40 + position * 13;
    }

    protected RealmsLabel addLabel(RealmsLabel label) {
        this.labels.add(label);
        return this.addRenderableOnly(label);
    }

    public Component createLabelNarration() {
        return CommonComponents.joinLines(this.labels.stream().map(RealmsLabel::getText).collect(Collectors.toList()));
    }
}
