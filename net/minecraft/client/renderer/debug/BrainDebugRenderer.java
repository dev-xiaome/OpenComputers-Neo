package net.minecraft.client.renderer.debug;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.network.protocol.common.custom.BrainDebugPayload;
import net.minecraft.network.protocol.game.DebugEntityNameGenerator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class BrainDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean SHOW_NAME_FOR_ALL = true;
    private static final boolean SHOW_PROFESSION_FOR_ALL = false;
    private static final boolean SHOW_BEHAVIORS_FOR_ALL = false;
    private static final boolean SHOW_ACTIVITIES_FOR_ALL = false;
    private static final boolean SHOW_INVENTORY_FOR_ALL = false;
    private static final boolean SHOW_GOSSIPS_FOR_ALL = false;
    private static final boolean SHOW_PATH_FOR_ALL = false;
    private static final boolean SHOW_HEALTH_FOR_ALL = false;
    private static final boolean SHOW_WANTS_GOLEM_FOR_ALL = true;
    private static final boolean SHOW_ANGER_LEVEL_FOR_ALL = false;
    private static final boolean SHOW_NAME_FOR_SELECTED = true;
    private static final boolean SHOW_PROFESSION_FOR_SELECTED = true;
    private static final boolean SHOW_BEHAVIORS_FOR_SELECTED = true;
    private static final boolean SHOW_ACTIVITIES_FOR_SELECTED = true;
    private static final boolean SHOW_MEMORIES_FOR_SELECTED = true;
    private static final boolean SHOW_INVENTORY_FOR_SELECTED = true;
    private static final boolean SHOW_GOSSIPS_FOR_SELECTED = true;
    private static final boolean SHOW_PATH_FOR_SELECTED = true;
    private static final boolean SHOW_HEALTH_FOR_SELECTED = true;
    private static final boolean SHOW_WANTS_GOLEM_FOR_SELECTED = true;
    private static final boolean SHOW_ANGER_LEVEL_FOR_SELECTED = true;
    private static final boolean SHOW_POI_INFO = true;
    private static final int MAX_RENDER_DIST_FOR_BRAIN_INFO = 30;
    private static final int MAX_RENDER_DIST_FOR_POI_INFO = 30;
    private static final int MAX_TARGETING_DIST = 8;
    private static final float TEXT_SCALE = 0.02F;
    private static final int WHITE = -1;
    private static final int YELLOW = -256;
    private static final int CYAN = -16711681;
    private static final int GREEN = -16711936;
    private static final int GRAY = -3355444;
    private static final int PINK = -98404;
    private static final int RED = -65536;
    private static final int ORANGE = -23296;
    private final Minecraft minecraft;
    private final Map<BlockPos, BrainDebugRenderer.PoiInfo> pois = Maps.newHashMap();
    private final Map<UUID, BrainDebugPayload.BrainDump> brainDumpsPerEntity = Maps.newHashMap();
    @Nullable
    private UUID lastLookedAtUuid;

    public BrainDebugRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void clear() {
        this.pois.clear();
        this.brainDumpsPerEntity.clear();
        this.lastLookedAtUuid = null;
    }

    public void addPoi(BrainDebugRenderer.PoiInfo poiInfo) {
        this.pois.put(poiInfo.pos, poiInfo);
    }

    public void removePoi(BlockPos pos) {
        this.pois.remove(pos);
    }

    public void setFreeTicketCount(BlockPos pos, int freeTicketCount) {
        BrainDebugRenderer.PoiInfo braindebugrenderer$poiinfo = this.pois.get(pos);
        if (braindebugrenderer$poiinfo == null) {
            LOGGER.warn("Strange, setFreeTicketCount was called for an unknown POI: {}", pos);
        } else {
            braindebugrenderer$poiinfo.freeTicketCount = freeTicketCount;
        }
    }

    public void addOrUpdateBrainDump(BrainDebugPayload.BrainDump brainDump) {
        this.brainDumpsPerEntity.put(brainDump.uuid(), brainDump);
    }

    public void removeBrainDump(int id) {
        this.brainDumpsPerEntity.values().removeIf(p_293654_ -> p_293654_.id() == id);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        this.clearRemovedEntities();
        this.doRender(poseStack, bufferSource, camX, camY, camZ);
        if (!this.minecraft.player.isSpectator()) {
            this.updateLastLookedAtUuid();
        }
    }

    private void clearRemovedEntities() {
        this.brainDumpsPerEntity.entrySet().removeIf(p_293652_ -> {
            Entity entity = this.minecraft.level.getEntity(p_293652_.getValue().id());
            return entity == null || entity.isRemoved();
        });
    }

    private void doRender(PoseStack poseStack, MultiBufferSource buffer, double x, double y, double z) {
        BlockPos blockpos = BlockPos.containing(x, y, z);
        this.brainDumpsPerEntity.values().forEach(p_293660_ -> {
            if (this.isPlayerCloseEnoughToMob(p_293660_)) {
                this.renderBrainInfo(poseStack, buffer, p_293660_, x, y, z);
            }
        });

        for (BlockPos blockpos1 : this.pois.keySet()) {
            if (blockpos.closerThan(blockpos1, 30.0)) {
                highlightPoi(poseStack, buffer, blockpos1);
            }
        }

        this.pois.values().forEach(p_269718_ -> {
            if (blockpos.closerThan(p_269718_.pos, 30.0)) {
                this.renderPoiInfo(poseStack, buffer, p_269718_);
            }
        });
        this.getGhostPois().forEach((p_269707_, p_269708_) -> {
            if (blockpos.closerThan(p_269707_, 30.0)) {
                this.renderGhostPoi(poseStack, buffer, p_269707_, (List<String>)p_269708_);
            }
        });
    }

    private static void highlightPoi(PoseStack poseStack, MultiBufferSource buffer, BlockPos pos) {
        float f = 0.05F;
        DebugRenderer.renderFilledBox(poseStack, buffer, pos, 0.05F, 0.2F, 0.2F, 1.0F, 0.3F);
    }

    private void renderGhostPoi(PoseStack poseStack, MultiBufferSource buffer, BlockPos pos, List<String> poiName) {
        float f = 0.05F;
        DebugRenderer.renderFilledBox(poseStack, buffer, pos, 0.05F, 0.2F, 0.2F, 1.0F, 0.3F);
        renderTextOverPos(poseStack, buffer, poiName + "", pos, 0, -256);
        renderTextOverPos(poseStack, buffer, "Ghost POI", pos, 1, -65536);
    }

    private void renderPoiInfo(PoseStack poseStack, MultiBufferSource buffer, BrainDebugRenderer.PoiInfo poiInfo) {
        int i = 0;
        Set<String> set = this.getTicketHolderNames(poiInfo);
        if (set.size() < 4) {
            renderTextOverPoi(poseStack, buffer, "Owners: " + set, poiInfo, i, -256);
        } else {
            renderTextOverPoi(poseStack, buffer, set.size() + " ticket holders", poiInfo, i, -256);
        }

        i++;
        Set<String> set1 = this.getPotentialTicketHolderNames(poiInfo);
        if (set1.size() < 4) {
            renderTextOverPoi(poseStack, buffer, "Candidates: " + set1, poiInfo, i, -23296);
        } else {
            renderTextOverPoi(poseStack, buffer, set1.size() + " potential owners", poiInfo, i, -23296);
        }

        renderTextOverPoi(poseStack, buffer, "Free tickets: " + poiInfo.freeTicketCount, poiInfo, ++i, -256);
        renderTextOverPoi(poseStack, buffer, poiInfo.type, poiInfo, ++i, -1);
    }

    private void renderPath(
        PoseStack poseStack, MultiBufferSource buffer, BrainDebugPayload.BrainDump brainDump, double x, double y, double z
    ) {
        if (brainDump.path() != null) {
            PathfindingRenderer.renderPath(poseStack, buffer, brainDump.path(), 0.5F, false, false, x, y, z);
        }
    }

    private void renderBrainInfo(
        PoseStack poseStack, MultiBufferSource buffer, BrainDebugPayload.BrainDump brainDump, double x, double y, double z
    ) {
        boolean flag = this.isMobSelected(brainDump);
        int i = 0;
        renderTextOverMob(poseStack, buffer, brainDump.pos(), i, brainDump.name(), -1, 0.03F);
        i++;
        if (flag) {
            renderTextOverMob(poseStack, buffer, brainDump.pos(), i, brainDump.profession() + " " + brainDump.xp() + " xp", -1, 0.02F);
            i++;
        }

        if (flag) {
            int j = brainDump.health() < brainDump.maxHealth() ? -23296 : -1;
            renderTextOverMob(
                poseStack,
                buffer,
                brainDump.pos(),
                i,
                "health: " + String.format(Locale.ROOT, "%.1f", brainDump.health()) + " / " + String.format(Locale.ROOT, "%.1f", brainDump.maxHealth()),
                j,
                0.02F
            );
            i++;
        }

        if (flag && !brainDump.inventory().equals("")) {
            renderTextOverMob(poseStack, buffer, brainDump.pos(), i, brainDump.inventory(), -98404, 0.02F);
            i++;
        }

        if (flag) {
            for (String s : brainDump.behaviors()) {
                renderTextOverMob(poseStack, buffer, brainDump.pos(), i, s, -16711681, 0.02F);
                i++;
            }
        }

        if (flag) {
            for (String s1 : brainDump.activities()) {
                renderTextOverMob(poseStack, buffer, brainDump.pos(), i, s1, -16711936, 0.02F);
                i++;
            }
        }

        if (brainDump.wantsGolem()) {
            renderTextOverMob(poseStack, buffer, brainDump.pos(), i, "Wants Golem", -23296, 0.02F);
            i++;
        }

        if (flag && brainDump.angerLevel() != -1) {
            renderTextOverMob(poseStack, buffer, brainDump.pos(), i, "Anger Level: " + brainDump.angerLevel(), -98404, 0.02F);
            i++;
        }

        if (flag) {
            for (String s2 : brainDump.gossips()) {
                if (s2.startsWith(brainDump.name())) {
                    renderTextOverMob(poseStack, buffer, brainDump.pos(), i, s2, -1, 0.02F);
                } else {
                    renderTextOverMob(poseStack, buffer, brainDump.pos(), i, s2, -23296, 0.02F);
                }

                i++;
            }
        }

        if (flag) {
            for (String s3 : Lists.reverse(brainDump.memories())) {
                renderTextOverMob(poseStack, buffer, brainDump.pos(), i, s3, -3355444, 0.02F);
                i++;
            }
        }

        if (flag) {
            this.renderPath(poseStack, buffer, brainDump, x, y, z);
        }
    }

    private static void renderTextOverPoi(
        PoseStack poseStack, MultiBufferSource buffer, String text, BrainDebugRenderer.PoiInfo poiInfo, int layer, int color
    ) {
        renderTextOverPos(poseStack, buffer, text, poiInfo.pos, layer, color);
    }

    private static void renderTextOverPos(PoseStack poseStack, MultiBufferSource buffer, String text, BlockPos pos, int layer, int color) {
        double d0 = 1.3;
        double d1 = 0.2;
        double d2 = (double)pos.getX() + 0.5;
        double d3 = (double)pos.getY() + 1.3 + (double)layer * 0.2;
        double d4 = (double)pos.getZ() + 0.5;
        DebugRenderer.renderFloatingText(poseStack, buffer, text, d2, d3, d4, color, 0.02F, true, 0.0F, true);
    }

    private static void renderTextOverMob(
        PoseStack poseStack, MultiBufferSource buffer, Position pos, int layer, String text, int color, float scale
    ) {
        double d0 = 2.4;
        double d1 = 0.25;
        BlockPos blockpos = BlockPos.containing(pos);
        double d2 = (double)blockpos.getX() + 0.5;
        double d3 = pos.y() + 2.4 + (double)layer * 0.25;
        double d4 = (double)blockpos.getZ() + 0.5;
        float f = 0.5F;
        DebugRenderer.renderFloatingText(poseStack, buffer, text, d2, d3, d4, color, scale, false, 0.5F, true);
    }

    private Set<String> getTicketHolderNames(BrainDebugRenderer.PoiInfo poiInfo) {
        return this.getTicketHolders(poiInfo.pos).stream().map(DebugEntityNameGenerator::getEntityName).collect(Collectors.toSet());
    }

    private Set<String> getPotentialTicketHolderNames(BrainDebugRenderer.PoiInfo poiInfo) {
        return this.getPotentialTicketHolders(poiInfo.pos).stream().map(DebugEntityNameGenerator::getEntityName).collect(Collectors.toSet());
    }

    private boolean isMobSelected(BrainDebugPayload.BrainDump brainDump) {
        return Objects.equals(this.lastLookedAtUuid, brainDump.uuid());
    }

    private boolean isPlayerCloseEnoughToMob(BrainDebugPayload.BrainDump brainDump) {
        Player player = this.minecraft.player;
        BlockPos blockpos = BlockPos.containing(player.getX(), brainDump.pos().y(), player.getZ());
        BlockPos blockpos1 = BlockPos.containing(brainDump.pos());
        return blockpos.closerThan(blockpos1, 30.0);
    }

    private Collection<UUID> getTicketHolders(BlockPos pos) {
        return this.brainDumpsPerEntity
            .values()
            .stream()
            .filter(p_293662_ -> p_293662_.hasPoi(pos))
            .map(BrainDebugPayload.BrainDump::uuid)
            .collect(Collectors.toSet());
    }

    private Collection<UUID> getPotentialTicketHolders(BlockPos pos) {
        return this.brainDumpsPerEntity
            .values()
            .stream()
            .filter(p_293664_ -> p_293664_.hasPotentialPoi(pos))
            .map(BrainDebugPayload.BrainDump::uuid)
            .collect(Collectors.toSet());
    }

    private Map<BlockPos, List<String>> getGhostPois() {
        Map<BlockPos, List<String>> map = Maps.newHashMap();

        for (BrainDebugPayload.BrainDump braindebugpayload$braindump : this.brainDumpsPerEntity.values()) {
            for (BlockPos blockpos : Iterables.concat(braindebugpayload$braindump.pois(), braindebugpayload$braindump.potentialPois())) {
                if (!this.pois.containsKey(blockpos)) {
                    map.computeIfAbsent(blockpos, p_113292_ -> Lists.newArrayList()).add(braindebugpayload$braindump.name());
                }
            }
        }

        return map;
    }

    private void updateLastLookedAtUuid() {
        DebugRenderer.getTargetedEntity(this.minecraft.getCameraEntity(), 8).ifPresent(p_113212_ -> this.lastLookedAtUuid = p_113212_.getUUID());
    }

    @OnlyIn(Dist.CLIENT)
    public static class PoiInfo {
        public final BlockPos pos;
        public final String type;
        public int freeTicketCount;

        public PoiInfo(BlockPos pos, String type, int freeTicketCount) {
            this.pos = pos;
            this.type = type;
            this.freeTicketCount = freeTicketCount;
        }
    }
}
