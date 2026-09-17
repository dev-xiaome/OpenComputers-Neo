package net.minecraft.client.renderer.debug;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ChunkDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    final Minecraft minecraft;
    private double lastUpdateTime = Double.MIN_VALUE;
    private final int radius = 12;
    @Nullable
    private ChunkDebugRenderer.ChunkData data;

    public ChunkDebugRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        double d0 = (double)Util.getNanos();
        if (d0 - this.lastUpdateTime > 3.0E9) {
            this.lastUpdateTime = d0;
            IntegratedServer integratedserver = this.minecraft.getSingleplayerServer();
            if (integratedserver != null) {
                this.data = new ChunkDebugRenderer.ChunkData(integratedserver, camX, camZ);
            } else {
                this.data = null;
            }
        }

        if (this.data != null) {
            Map<ChunkPos, String> map = this.data.serverData.getNow(null);
            double d1 = this.minecraft.gameRenderer.getMainCamera().getPosition().y * 0.85;

            for (Entry<ChunkPos, String> entry : this.data.clientData.entrySet()) {
                ChunkPos chunkpos = entry.getKey();
                String s = entry.getValue();
                if (map != null) {
                    s = s + map.get(chunkpos);
                }

                String[] astring = s.split("\n");
                int i = 0;

                for (String s1 : astring) {
                    DebugRenderer.renderFloatingText(
                        poseStack,
                        bufferSource,
                        s1,
                        (double)SectionPos.sectionToBlockCoord(chunkpos.x, 8),
                        d1 + (double)i,
                        (double)SectionPos.sectionToBlockCoord(chunkpos.z, 8),
                        -1,
                        0.15F,
                        true,
                        0.0F,
                        true
                    );
                    i -= 2;
                }
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    final class ChunkData {
        final Map<ChunkPos, String> clientData;
        final CompletableFuture<Map<ChunkPos, String>> serverData;

        ChunkData(IntegratedServer integratedServer, double x, double z) {
            ClientLevel clientlevel = ChunkDebugRenderer.this.minecraft.level;
            ResourceKey<Level> resourcekey = clientlevel.dimension();
            int i = SectionPos.posToSectionCoord(x);
            int j = SectionPos.posToSectionCoord(z);
            Builder<ChunkPos, String> builder = ImmutableMap.builder();
            ClientChunkCache clientchunkcache = clientlevel.getChunkSource();

            for (int k = i - 12; k <= i + 12; k++) {
                for (int l = j - 12; l <= j + 12; l++) {
                    ChunkPos chunkpos = new ChunkPos(k, l);
                    String s = "";
                    LevelChunk levelchunk = clientchunkcache.getChunk(k, l, false);
                    s = s + "Client: ";
                    if (levelchunk == null) {
                        s = s + "0n/a\n";
                    } else {
                        s = s + (levelchunk.isEmpty() ? " E" : "");
                        s = s + "\n";
                    }

                    builder.put(chunkpos, s);
                }
            }

            this.clientData = builder.build();
            this.serverData = integratedServer.submit(() -> {
                ServerLevel serverlevel = integratedServer.getLevel(resourcekey);
                if (serverlevel == null) {
                    return ImmutableMap.of();
                } else {
                    Builder<ChunkPos, String> builder1 = ImmutableMap.builder();
                    ServerChunkCache serverchunkcache = serverlevel.getChunkSource();

                    for (int i1 = i - 12; i1 <= i + 12; i1++) {
                        for (int j1 = j - 12; j1 <= j + 12; j1++) {
                            ChunkPos chunkpos1 = new ChunkPos(i1, j1);
                            builder1.put(chunkpos1, "Server: " + serverchunkcache.getChunkDebugData(chunkpos1));
                        }
                    }

                    return builder1.build();
                }
            });
        }
    }
}
