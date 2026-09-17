package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ChunkToProtochunkFix extends DataFix {
    private static final int NUM_SECTIONS = 16;

    public ChunkToProtochunkFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.writeFixAndRead(
            "ChunkToProtoChunkFix",
            this.getInputSchema().getType(References.CHUNK),
            this.getOutputSchema().getType(References.CHUNK),
            p_199886_ -> p_199886_.update("Level", ChunkToProtochunkFix::fixChunkData)
        );
    }

    private static <T> Dynamic<T> fixChunkData(Dynamic<T> chunkData) {
        boolean flag = chunkData.get("TerrainPopulated").asBoolean(false);
        boolean flag1 = chunkData.get("LightPopulated").asNumber().result().isEmpty() || chunkData.get("LightPopulated").asBoolean(false);
        String s;
        if (flag) {
            if (flag1) {
                s = "mobs_spawned";
            } else {
                s = "decorated";
            }
        } else {
            s = "carved";
        }

        return repackTicks(repackBiomes(chunkData)).set("Status", chunkData.createString(s)).set("hasLegacyStructureData", chunkData.createBoolean(true));
    }

    private static <T> Dynamic<T> repackBiomes(Dynamic<T> dynamic) {
        return dynamic.update("Biomes", p_337614_ -> DataFixUtils.orElse(p_337614_.asByteBufferOpt().result().map(p_199868_ -> {
                int[] aint = new int[256];

                for (int i = 0; i < aint.length; i++) {
                    if (i < p_199868_.capacity()) {
                        aint[i] = p_199868_.get(i) & 255;
                    }
                }

                return dynamic.createIntList(Arrays.stream(aint));
            }), p_337614_));
    }

    private static <T> Dynamic<T> repackTicks(Dynamic<T> dynamic) {
        return DataFixUtils.orElse(
            dynamic.get("TileTicks")
                .asStreamOpt()
                .result()
                .map(
                    p_199871_ -> {
                        List<ShortList> list = IntStream.range(0, 16).mapToObj(p_199850_ -> new ShortArrayList()).collect(Collectors.toList());
                        p_199871_.forEach(p_199874_ -> {
                            int i = p_199874_.get("x").asInt(0);
                            int j = p_199874_.get("y").asInt(0);
                            int k = p_199874_.get("z").asInt(0);
                            short short1 = packOffsetCoordinates(i, j, k);
                            list.get(j >> 4).add(short1);
                        });
                        return dynamic.remove("TileTicks")
                            .set(
                                "ToBeTicked",
                                dynamic.createList(
                                    list.stream()
                                        .map(
                                            p_199865_ -> dynamic.createList(
                                                    p_199865_.intStream().mapToObj(p_199859_ -> dynamic.createShort((short)p_199859_))
                                                )
                                        )
                                )
                            );
                    }
                ),
            dynamic
        );
    }

    private static short packOffsetCoordinates(int x, int y, int z) {
        return (short)(x & 15 | (y & 15) << 4 | (z & 15) << 8);
    }
}
