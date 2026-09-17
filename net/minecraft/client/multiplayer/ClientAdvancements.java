package net.minecraft.client.multiplayer;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ClientAdvancements {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Minecraft minecraft;
    private final WorldSessionTelemetryManager telemetryManager;
    private final AdvancementTree tree = new AdvancementTree();
    private final Map<AdvancementHolder, AdvancementProgress> progress = new Object2ObjectOpenHashMap<>();
    @Nullable
    private ClientAdvancements.Listener listener;
    @Nullable
    private AdvancementHolder selectedTab;

    public ClientAdvancements(Minecraft minecraft, WorldSessionTelemetryManager telemetryManager) {
        this.minecraft = minecraft;
        this.telemetryManager = telemetryManager;
    }

    public void update(ClientboundUpdateAdvancementsPacket packet) {
        if (packet.shouldReset()) {
            this.tree.clear();
            this.progress.clear();
        }

        this.tree.remove(packet.getRemoved());
        this.tree.addAll(packet.getAdded());

        for (Entry<ResourceLocation, AdvancementProgress> entry : packet.getProgress().entrySet()) {
            AdvancementNode advancementnode = this.tree.get(entry.getKey());
            if (advancementnode != null) {
                AdvancementProgress advancementprogress = entry.getValue();
                advancementprogress.update(advancementnode.advancement().requirements());
                this.progress.put(advancementnode.holder(), advancementprogress);
                if (this.listener != null) {
                    this.listener.onUpdateAdvancementProgress(advancementnode, advancementprogress);
                }

                if (!packet.shouldReset() && advancementprogress.isDone()) {
                    if (this.minecraft.level != null) {
                        this.telemetryManager.onAdvancementDone(this.minecraft.level, advancementnode.holder());
                    }

                    Optional<DisplayInfo> optional = advancementnode.advancement().display();
                    if (optional.isPresent() && optional.get().shouldShowToast()) {
                        this.minecraft.getToasts().addToast(new AdvancementToast(advancementnode.holder()));
                    }
                }
            } else {
                LOGGER.warn("Server informed client about progress for unknown advancement {}", entry.getKey());
            }
        }
    }

    public AdvancementTree getTree() {
        return this.tree;
    }

    public void setSelectedTab(@Nullable AdvancementHolder advancement, boolean tellServer) {
        ClientPacketListener clientpacketlistener = this.minecraft.getConnection();
        if (clientpacketlistener != null && advancement != null && tellServer) {
            clientpacketlistener.send(ServerboundSeenAdvancementsPacket.openedTab(advancement));
        }

        if (this.selectedTab != advancement) {
            this.selectedTab = advancement;
            if (this.listener != null) {
                this.listener.onSelectedTabChanged(advancement);
            }
        }
    }

    public void setListener(@Nullable ClientAdvancements.Listener listener) {
        this.listener = listener;
        this.tree.setListener(listener);
        if (listener != null) {
            this.progress.forEach((p_300950_, p_301173_) -> {
                AdvancementNode advancementnode = this.tree.get(p_300950_);
                if (advancementnode != null) {
                    listener.onUpdateAdvancementProgress(advancementnode, p_301173_);
                }
            });
            listener.onSelectedTabChanged(this.selectedTab);
        }
    }

    @Nullable
    public AdvancementHolder get(ResourceLocation id) {
        AdvancementNode advancementnode = this.tree.get(id);
        return advancementnode != null ? advancementnode.holder() : null;
    }

    @OnlyIn(Dist.CLIENT)
    public interface Listener extends AdvancementTree.Listener {
        void onUpdateAdvancementProgress(AdvancementNode advancement, AdvancementProgress advancementProgress);

        void onSelectedTabChanged(@Nullable AdvancementHolder advancement);
    }
}
