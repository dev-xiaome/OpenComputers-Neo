package net.minecraft.client.gui.screens;

import com.ibm.icu.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CreateBuffetWorldScreen extends Screen {
    private static final Component BIOME_SELECT_INFO = Component.translatable("createWorld.customize.buffet.biome").withColor(-8355712);
    private static final int SPACING = 8;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final Screen parent;
    private final Consumer<Holder<Biome>> applySettings;
    final Registry<Biome> biomes;
    private CreateBuffetWorldScreen.BiomeList list;
    Holder<Biome> biome;
    private Button doneButton;

    public CreateBuffetWorldScreen(Screen parent, WorldCreationContext context, Consumer<Holder<Biome>> applySettings) {
        super(Component.translatable("createWorld.customize.buffet.title"));
        this.parent = parent;
        this.applySettings = applySettings;
        this.biomes = context.worldgenLoadContext().registryOrThrow(Registries.BIOME);
        Holder<Biome> holder = this.biomes.getHolder(Biomes.PLAINS).or(() -> this.biomes.holders().findAny()).orElseThrow();
        this.biome = context.selectedDimensions().overworld().getBiomeSource().possibleBiomes().stream().findFirst().orElse(holder);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    protected void init() {
        LinearLayout linearlayout = this.layout.addToHeader(LinearLayout.vertical().spacing(8));
        linearlayout.defaultCellSetting().alignHorizontallyCenter();
        linearlayout.addChild(new StringWidget(this.getTitle(), this.font));
        linearlayout.addChild(new StringWidget(BIOME_SELECT_INFO, this.font));
        this.list = this.layout.addToContents(new CreateBuffetWorldScreen.BiomeList());
        LinearLayout linearlayout1 = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        this.doneButton = linearlayout1.addChild(Button.builder(CommonComponents.GUI_DONE, p_329718_ -> {
            this.applySettings.accept(this.biome);
            this.onClose();
        }).build());
        linearlayout1.addChild(Button.builder(CommonComponents.GUI_CANCEL, p_329719_ -> this.onClose()).build());
        this.list.setSelected(this.list.children().stream().filter(p_232738_ -> Objects.equals(p_232738_.biome, this.biome)).findFirst().orElse(null));
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        this.list.updateSize(this.width, this.layout);
    }

    void updateButtonValidity() {
        this.doneButton.active = this.list.getSelected() != null;
    }

    @OnlyIn(Dist.CLIENT)
    class BiomeList extends ObjectSelectionList<CreateBuffetWorldScreen.BiomeList.Entry> {
        BiomeList() {
            super(CreateBuffetWorldScreen.this.minecraft, CreateBuffetWorldScreen.this.width, CreateBuffetWorldScreen.this.height - 77, 40, 16);
            Collator collator = Collator.getInstance(Locale.getDefault());
            CreateBuffetWorldScreen.this.biomes
                .holders()
                .map(p_205389_ -> new CreateBuffetWorldScreen.BiomeList.Entry((Holder.Reference<Biome>)p_205389_))
                .sorted(Comparator.comparing(p_203142_ -> p_203142_.name.getString(), collator))
                .forEach(p_203138_ -> this.addEntry(p_203138_));
        }

        public void setSelected(@Nullable CreateBuffetWorldScreen.BiomeList.Entry entry) {
            super.setSelected(entry);
            if (entry != null) {
                CreateBuffetWorldScreen.this.biome = entry.biome;
            }

            CreateBuffetWorldScreen.this.updateButtonValidity();
        }

        @OnlyIn(Dist.CLIENT)
        class Entry extends ObjectSelectionList.Entry<CreateBuffetWorldScreen.BiomeList.Entry> {
            final Holder.Reference<Biome> biome;
            final Component name;

            public Entry(Holder.Reference<Biome> biome) {
                this.biome = biome;
                ResourceLocation resourcelocation = biome.key().location();
                String s = resourcelocation.toLanguageKey("biome");
                if (Language.getInstance().has(s)) {
                    this.name = Component.translatable(s);
                } else {
                    this.name = Component.literal(resourcelocation.toString());
                }
            }

            @Override
            public Component getNarration() {
                return Component.translatable("narrator.select", this.name);
            }

            @Override
            public void render(
                GuiGraphics guiGraphics,
                int index,
                int top,
                int left,
                int width,
                int height,
                int mouseX,
                int mouseY,
                boolean hovering,
                float partialTick
            ) {
                guiGraphics.drawString(CreateBuffetWorldScreen.this.font, this.name, left + 5, top + 2, 16777215);
            }

            /**
             * Called when a mouse button is clicked within the GUI element.
             * <p>
             * @return {@code true} if the event is consumed, {@code false} otherwise.
             *
             * @param mouseX the X coordinate of the mouse.
             * @param mouseY the Y coordinate of the mouse.
             * @param button the button that was clicked.
             */
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                BiomeList.this.setSelected(this);
                return super.mouseClicked(mouseX, mouseY, button);
            }
        }
    }
}
