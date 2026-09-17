package net.minecraft.client.renderer.debug;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.network.protocol.common.custom.BeeDebugPayload;
import net.minecraft.network.protocol.common.custom.HiveDebugPayload;
import net.minecraft.network.protocol.game.DebugEntityNameGenerator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BeeDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final boolean SHOW_GOAL_FOR_ALL_BEES = true;
    private static final boolean SHOW_NAME_FOR_ALL_BEES = true;
    private static final boolean SHOW_HIVE_FOR_ALL_BEES = true;
    private static final boolean SHOW_FLOWER_POS_FOR_ALL_BEES = true;
    private static final boolean SHOW_TRAVEL_TICKS_FOR_ALL_BEES = true;
    private static final boolean SHOW_PATH_FOR_ALL_BEES = false;
    private static final boolean SHOW_GOAL_FOR_SELECTED_BEE = true;
    private static final boolean SHOW_NAME_FOR_SELECTED_BEE = true;
    private static final boolean SHOW_HIVE_FOR_SELECTED_BEE = true;
    private static final boolean SHOW_FLOWER_POS_FOR_SELECTED_BEE = true;
    private static final boolean SHOW_TRAVEL_TICKS_FOR_SELECTED_BEE = true;
    private static final boolean SHOW_PATH_FOR_SELECTED_BEE = true;
    private static final boolean SHOW_HIVE_MEMBERS = true;
    private static final boolean SHOW_BLACKLISTS = true;
    private static final int MAX_RENDER_DIST_FOR_HIVE_OVERLAY = 30;
    private static final int MAX_RENDER_DIST_FOR_BEE_OVERLAY = 30;
    private static final int MAX_TARGETING_DIST = 8;
    private static final int HIVE_TIMEOUT = 20;
    private static final float TEXT_SCALE = 0.02F;
    private static final int WHITE = -1;
    private static final int YELLOW = -256;
    private static final int ORANGE = -23296;
    private static final int GREEN = -16711936;
    private static final int GRAY = -3355444;
    private static final int PINK = -98404;
    private static final int RED = -65536;
    private final Minecraft minecraft;
    private final Map<BlockPos, BeeDebugRenderer.HiveDebugInfo> hives = new HashMap<>();
    private final Map<UUID, BeeDebugPayload.BeeInfo> beeInfosPerEntity = new HashMap<>();
    @Nullable
    private UUID lastLookedAtUuid;

    public BeeDebugRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void clear() {
        this.hives.clear();
        this.beeInfosPerEntity.clear();
        this.lastLookedAtUuid = null;
    }

    public void addOrUpdateHiveInfo(HiveDebugPayload.HiveInfo hiveInfo, long lastSeen) {
        this.hives.put(hiveInfo.pos(), new BeeDebugRenderer.HiveDebugInfo(hiveInfo, lastSeen));
    }

    public void addOrUpdateBeeInfo(BeeDebugPayload.BeeInfo beeInfo) {
        this.beeInfosPerEntity.put(beeInfo.uuid(), beeInfo);
    }

    public void removeBeeInfo(int id) {
        this.beeInfosPerEntity.values().removeIf(p_293626_ -> p_293626_.id() == id);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        this.clearRemovedHives();
        this.clearRemovedBees();
        this.doRender(poseStack, bufferSource);
        if (!this.minecraft.player.isSpectator()) {
            this.updateLastLookedAtUuid();
        }
    }

    private void clearRemovedBees() {
        this.beeInfosPerEntity.entrySet().removeIf(p_293633_ -> this.minecraft.level.getEntity(p_293633_.getValue().id()) == null);
    }

    private void clearRemovedHives() {
        long i = this.minecraft.level.getGameTime() - 20L;
        this.hives.entrySet().removeIf(p_293628_ -> p_293628_.getValue().lastSeen() < i);
    }

    private void doRender(PoseStack poseStack, MultiBufferSource buffer) {
        BlockPos blockpos = this.getCamera().getBlockPosition();
        this.beeInfosPerEntity.values().forEach(p_293636_ -> {
            if (this.isPlayerCloseEnoughToMob(p_293636_)) {
                this.renderBeeInfo(poseStack, buffer, p_293636_);
            }
        });
        this.renderFlowerInfos(poseStack, buffer);

        for (BlockPos blockpos1 : this.hives.keySet()) {
            if (blockpos.closerThan(blockpos1, 30.0)) {
                highlightHive(poseStack, buffer, blockpos1);
            }
        }

        Map<BlockPos, Set<UUID>> map = this.createHiveBlacklistMap();
        this.hives.values().forEach(p_293646_ -> {
            if (blockpos.closerThan(p_293646_.info.pos(), 30.0)) {
                Set<UUID> set = map.get(p_293646_.info.pos());
                this.renderHiveInfo(poseStack, buffer, p_293646_.info, (Collection<UUID>)(set == null ? Sets.newHashSet() : set));
            }
        });
        this.getGhostHives().forEach((p_269699_, p_269700_) -> {
            if (blockpos.closerThan(p_269699_, 30.0)) {
                this.renderGhostHive(poseStack, buffer, p_269699_, (List<String>)p_269700_);
            }
        });
    }

    private Map<BlockPos, Set<UUID>> createHiveBlacklistMap() {
        Map<BlockPos, Set<UUID>> map = Maps.newHashMap();
        this.beeInfosPerEntity
            .values()
            .forEach(
                p_293638_ -> p_293638_.blacklistedHives()
                        .forEach(p_293641_ -> map.computeIfAbsent(p_293641_, p_173777_ -> Sets.newHashSet()).add(p_293638_.uuid()))
            );
        return map;
    }

    private void renderFlowerInfos(PoseStack poseStack, MultiBufferSource buffer) {
        Map<BlockPos, Set<UUID>> map = Maps.newHashMap();
        this.beeInfosPerEntity.values().forEach(p_293651_ -> {
            if (p_293651_.flowerPos() != null) {
                map.computeIfAbsent(p_293651_.flowerPos(), p_293649_ -> new HashSet<>()).add(p_293651_.uuid());
            }
        });
        map.forEach((p_339302_, p_339303_) -> {
            Set<String> set = p_339303_.stream().map(DebugEntityNameGenerator::getEntityName).collect(Collectors.toSet());
            int i = 1;
            renderTextOverPos(poseStack, buffer, set.toString(), p_339302_, i++, -256);
            renderTextOverPos(poseStack, buffer, "Flower", p_339302_, i++, -1);
            float f = 0.05F;
            DebugRenderer.renderFilledBox(poseStack, buffer, p_339302_, 0.05F, 0.8F, 0.8F, 0.0F, 0.3F);
        });
    }

    private static String getBeeUuidsAsString(Collection<UUID> beeUuids) {
        if (beeUuids.isEmpty()) {
            return "-";
        } else {
            return beeUuids.size() > 3
                ? beeUuids.size() + " bees"
                : beeUuids.stream().map(DebugEntityNameGenerator::getEntityName).collect(Collectors.toSet()).toString();
        }
    }

    private static void highlightHive(PoseStack poseStack, MultiBufferSource buffer, BlockPos hivePos) {
        float f = 0.05F;
        DebugRenderer.renderFilledBox(poseStack, buffer, hivePos, 0.05F, 0.2F, 0.2F, 1.0F, 0.3F);
    }

    private void renderGhostHive(PoseStack poseStack, MultiBufferSource buffer, BlockPos hivePos, List<String> ghostHives) {
        float f = 0.05F;
        DebugRenderer.renderFilledBox(poseStack, buffer, hivePos, 0.05F, 0.2F, 0.2F, 1.0F, 0.3F);
        renderTextOverPos(poseStack, buffer, ghostHives + "", hivePos, 0, -256);
        renderTextOverPos(poseStack, buffer, "Ghost Hive", hivePos, 1, -65536);
    }

    private void renderHiveInfo(PoseStack poseStack, MultiBufferSource buffer, HiveDebugPayload.HiveInfo hiveInfo, Collection<UUID> beeUuids) {
        int i = 0;
        if (!beeUuids.isEmpty()) {
            renderTextOverHive(poseStack, buffer, "Blacklisted by " + getBeeUuidsAsString(beeUuids), hiveInfo, i++, -65536);
        }

        renderTextOverHive(poseStack, buffer, "Out: " + getBeeUuidsAsString(this.getHiveMembers(hiveInfo.pos())), hiveInfo, i++, -3355444);
        if (hiveInfo.occupantCount() == 0) {
            renderTextOverHive(poseStack, buffer, "In: -", hiveInfo, i++, -256);
        } else if (hiveInfo.occupantCount() == 1) {
            renderTextOverHive(poseStack, buffer, "In: 1 bee", hiveInfo, i++, -256);
        } else {
            renderTextOverHive(poseStack, buffer, "In: " + hiveInfo.occupantCount() + " bees", hiveInfo, i++, -256);
        }

        renderTextOverHive(poseStack, buffer, "Honey: " + hiveInfo.honeyLevel(), hiveInfo, i++, -23296);
        renderTextOverHive(poseStack, buffer, hiveInfo.hiveType() + (hiveInfo.sedated() ? " (sedated)" : ""), hiveInfo, i++, -1);
    }

    private void renderPath(PoseStack poseStack, MultiBufferSource buffer, BeeDebugPayload.BeeInfo beeInfo) {
        if (beeInfo.path() != null) {
            PathfindingRenderer.renderPath(
                poseStack,
                buffer,
                beeInfo.path(),
                0.5F,
                false,
                false,
                this.getCamera().getPosition().x(),
                this.getCamera().getPosition().y(),
                this.getCamera().getPosition().z()
            );
        }
    }

    private void renderBeeInfo(PoseStack poseStack, MultiBufferSource buffer, BeeDebugPayload.BeeInfo beeInfo) {
        boolean flag = this.isBeeSelected(beeInfo);
        int i = 0;
        renderTextOverMob(poseStack, buffer, beeInfo.pos(), i++, beeInfo.toString(), -1, 0.03F);
        if (beeInfo.hivePos() == null) {
            renderTextOverMob(poseStack, buffer, beeInfo.pos(), i++, "No hive", -98404, 0.02F);
        } else {
            renderTextOverMob(poseStack, buffer, beeInfo.pos(), i++, "Hive: " + this.getPosDescription(beeInfo, beeInfo.hivePos()), -256, 0.02F);
        }

        if (beeInfo.flowerPos() == null) {
            renderTextOverMob(poseStack, buffer, beeInfo.pos(), i++, "No flower", -98404, 0.02F);
        } else {
            renderTextOverMob(poseStack, buffer, beeInfo.pos(), i++, "Flower: " + this.getPosDescription(beeInfo, beeInfo.flowerPos()), -256, 0.02F);
        }

        for (String s : beeInfo.goals()) {
            renderTextOverMob(poseStack, buffer, beeInfo.pos(), i++, s, -16711936, 0.02F);
        }

        if (flag) {
            this.renderPath(poseStack, buffer, beeInfo);
        }

        if (beeInfo.travelTicks() > 0) {
            int j = beeInfo.travelTicks() < 600 ? -3355444 : -23296;
            renderTextOverMob(poseStack, buffer, beeInfo.pos(), i++, "Travelling: " + beeInfo.travelTicks() + " ticks", j, 0.02F);
        }
    }

    private static void renderTextOverHive(
        PoseStack poseStack, MultiBufferSource buffer, String text, HiveDebugPayload.HiveInfo hiveInfo, int layer, int color
    ) {
        renderTextOverPos(poseStack, buffer, text, hiveInfo.pos(), layer, color);
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

    private Camera getCamera() {
        return this.minecraft.gameRenderer.getMainCamera();
    }

    private Set<String> getHiveMemberNames(HiveDebugPayload.HiveInfo hiveInfo) {
        return this.getHiveMembers(hiveInfo.pos()).stream().map(DebugEntityNameGenerator::getEntityName).collect(Collectors.toSet());
    }

    private String getPosDescription(BeeDebugPayload.BeeInfo beeInfo, BlockPos pos) {
        double d0 = Math.sqrt(pos.distToCenterSqr(beeInfo.pos()));
        double d1 = (double)Math.round(d0 * 10.0) / 10.0;
        return pos.toShortString() + " (dist " + d1 + ")";
    }

    private boolean isBeeSelected(BeeDebugPayload.BeeInfo beeInfo) {
        return Objects.equals(this.lastLookedAtUuid, beeInfo.uuid());
    }

    private boolean isPlayerCloseEnoughToMob(BeeDebugPayload.BeeInfo beeInfo) {
        Player player = this.minecraft.player;
        BlockPos blockpos = BlockPos.containing(player.getX(), beeInfo.pos().y(), player.getZ());
        BlockPos blockpos1 = BlockPos.containing(beeInfo.pos());
        return blockpos.closerThan(blockpos1, 30.0);
    }

    private Collection<UUID> getHiveMembers(BlockPos pos) {
        return this.beeInfosPerEntity
            .values()
            .stream()
            .filter(p_293648_ -> p_293648_.hasHive(pos))
            .map(BeeDebugPayload.BeeInfo::uuid)
            .collect(Collectors.toSet());
    }

    private Map<BlockPos, List<String>> getGhostHives() {
        Map<BlockPos, List<String>> map = Maps.newHashMap();

        for (BeeDebugPayload.BeeInfo beedebugpayload$beeinfo : this.beeInfosPerEntity.values()) {
            if (beedebugpayload$beeinfo.hivePos() != null && !this.hives.containsKey(beedebugpayload$beeinfo.hivePos())) {
                map.computeIfAbsent(beedebugpayload$beeinfo.hivePos(), p_113140_ -> Lists.newArrayList()).add(beedebugpayload$beeinfo.generateName());
            }
        }

        return map;
    }

    private void updateLastLookedAtUuid() {
        DebugRenderer.getTargetedEntity(this.minecraft.getCameraEntity(), 8).ifPresent(p_113059_ -> this.lastLookedAtUuid = p_113059_.getUUID());
    }

    @OnlyIn(Dist.CLIENT)
    static record HiveDebugInfo(HiveDebugPayload.HiveInfo info, long lastSeen) {
    }
}
