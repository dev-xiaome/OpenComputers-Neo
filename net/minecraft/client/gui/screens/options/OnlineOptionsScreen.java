package net.minecraft.client.gui.screens.options;

import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class OnlineOptionsScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("options.online.title");
    @Nullable
    private OptionInstance<Unit> difficultyDisplay;

    public OnlineOptionsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, TITLE);
    }

    @Override
    protected void init() {
        super.init();
        if (this.difficultyDisplay != null) {
            AbstractWidget abstractwidget = this.list.findOption(this.difficultyDisplay);
            if (abstractwidget != null) {
                abstractwidget.active = false;
            }
        }
    }

    private OptionInstance<?>[] options(Options options, Minecraft minecraft) {
        List<OptionInstance<?>> list = new ArrayList<>();
        list.add(options.realmsNotifications());
        list.add(options.allowServerListing());
        OptionInstance<Unit> optioninstance = Optionull.map(
            minecraft.level,
            p_346363_ -> {
                Difficulty difficulty = p_346363_.getDifficulty();
                return new OptionInstance<>(
                    "options.difficulty.online",
                    OptionInstance.noTooltip(),
                    (p_345525_, p_345135_) -> difficulty.getDisplayName(),
                    new OptionInstance.Enum<>(List.of(Unit.INSTANCE), Codec.EMPTY.codec()),
                    Unit.INSTANCE,
                    p_346107_ -> {
                    }
                );
            }
        );
        if (optioninstance != null) {
            this.difficultyDisplay = optioninstance;
            list.add(optioninstance);
        }

        return list.toArray(new OptionInstance[0]);
    }

    @Override
    protected void addOptions() {
        this.list.addSmall(this.options(this.options, this.minecraft));
    }
}
