package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.util.datafix.PackedBitStorage;
import org.slf4j.Logger;

public class ChunkPalettedStorageFix extends DataFix {
    private static final int NORTH_WEST_MASK = 128;
    private static final int WEST_MASK = 64;
    private static final int SOUTH_WEST_MASK = 32;
    private static final int SOUTH_MASK = 16;
    private static final int SOUTH_EAST_MASK = 8;
    private static final int EAST_MASK = 4;
    private static final int NORTH_EAST_MASK = 2;
    private static final int NORTH_MASK = 1;
    static final Logger LOGGER = LogUtils.getLogger();
    static final BitSet VIRTUAL = new BitSet(256);
    static final BitSet FIX = new BitSet(256);
    static final Dynamic<?> PUMPKIN = BlockStateData.parse("{Name:'minecraft:pumpkin'}");
    static final Dynamic<?> SNOWY_PODZOL = BlockStateData.parse("{Name:'minecraft:podzol',Properties:{snowy:'true'}}");
    static final Dynamic<?> SNOWY_GRASS = BlockStateData.parse("{Name:'minecraft:grass_block',Properties:{snowy:'true'}}");
    static final Dynamic<?> SNOWY_MYCELIUM = BlockStateData.parse("{Name:'minecraft:mycelium',Properties:{snowy:'true'}}");
    static final Dynamic<?> UPPER_SUNFLOWER = BlockStateData.parse("{Name:'minecraft:sunflower',Properties:{half:'upper'}}");
    static final Dynamic<?> UPPER_LILAC = BlockStateData.parse("{Name:'minecraft:lilac',Properties:{half:'upper'}}");
    static final Dynamic<?> UPPER_TALL_GRASS = BlockStateData.parse("{Name:'minecraft:tall_grass',Properties:{half:'upper'}}");
    static final Dynamic<?> UPPER_LARGE_FERN = BlockStateData.parse("{Name:'minecraft:large_fern',Properties:{half:'upper'}}");
    static final Dynamic<?> UPPER_ROSE_BUSH = BlockStateData.parse("{Name:'minecraft:rose_bush',Properties:{half:'upper'}}");
    static final Dynamic<?> UPPER_PEONY = BlockStateData.parse("{Name:'minecraft:peony',Properties:{half:'upper'}}");
    static final Map<String, Dynamic<?>> FLOWER_POT_MAP = DataFixUtils.make(Maps.newHashMap(), p_15111_ -> {
        p_15111_.put("minecraft:air0", BlockStateData.parse("{Name:'minecraft:flower_pot'}"));
        p_15111_.put("minecraft:red_flower0", BlockStateData.parse("{Name:'minecraft:potted_poppy'}"));
        p_15111_.put("minecraft:red_flower1", BlockStateData.parse("{Name:'minecraft:potted_blue_orchid'}"));
        p_15111_.put("minecraft:red_flower2", BlockStateData.parse("{Name:'minecraft:potted_allium'}"));
        p_15111_.put("minecraft:red_flower3", BlockStateData.parse("{Name:'minecraft:potted_azure_bluet'}"));
        p_15111_.put("minecraft:red_flower4", BlockStateData.parse("{Name:'minecraft:potted_red_tulip'}"));
        p_15111_.put("minecraft:red_flower5", BlockStateData.parse("{Name:'minecraft:potted_orange_tulip'}"));
        p_15111_.put("minecraft:red_flower6", BlockStateData.parse("{Name:'minecraft:potted_white_tulip'}"));
        p_15111_.put("minecraft:red_flower7", BlockStateData.parse("{Name:'minecraft:potted_pink_tulip'}"));
        p_15111_.put("minecraft:red_flower8", BlockStateData.parse("{Name:'minecraft:potted_oxeye_daisy'}"));
        p_15111_.put("minecraft:yellow_flower0", BlockStateData.parse("{Name:'minecraft:potted_dandelion'}"));
        p_15111_.put("minecraft:sapling0", BlockStateData.parse("{Name:'minecraft:potted_oak_sapling'}"));
        p_15111_.put("minecraft:sapling1", BlockStateData.parse("{Name:'minecraft:potted_spruce_sapling'}"));
        p_15111_.put("minecraft:sapling2", BlockStateData.parse("{Name:'minecraft:potted_birch_sapling'}"));
        p_15111_.put("minecraft:sapling3", BlockStateData.parse("{Name:'minecraft:potted_jungle_sapling'}"));
        p_15111_.put("minecraft:sapling4", BlockStateData.parse("{Name:'minecraft:potted_acacia_sapling'}"));
        p_15111_.put("minecraft:sapling5", BlockStateData.parse("{Name:'minecraft:potted_dark_oak_sapling'}"));
        p_15111_.put("minecraft:red_mushroom0", BlockStateData.parse("{Name:'minecraft:potted_red_mushroom'}"));
        p_15111_.put("minecraft:brown_mushroom0", BlockStateData.parse("{Name:'minecraft:potted_brown_mushroom'}"));
        p_15111_.put("minecraft:deadbush0", BlockStateData.parse("{Name:'minecraft:potted_dead_bush'}"));
        p_15111_.put("minecraft:tallgrass2", BlockStateData.parse("{Name:'minecraft:potted_fern'}"));
        p_15111_.put("minecraft:cactus0", BlockStateData.getTag(2240));
    });
    static final Map<String, Dynamic<?>> SKULL_MAP = DataFixUtils.make(Maps.newHashMap(), p_15108_ -> {
        mapSkull(p_15108_, 0, "skeleton", "skull");
        mapSkull(p_15108_, 1, "wither_skeleton", "skull");
        mapSkull(p_15108_, 2, "zombie", "head");
        mapSkull(p_15108_, 3, "player", "head");
        mapSkull(p_15108_, 4, "creeper", "head");
        mapSkull(p_15108_, 5, "dragon", "head");
    });
    static final Map<String, Dynamic<?>> DOOR_MAP = DataFixUtils.make(Maps.newHashMap(), p_15105_ -> {
        mapDoor(p_15105_, "oak_door", 1024);
        mapDoor(p_15105_, "iron_door", 1136);
        mapDoor(p_15105_, "spruce_door", 3088);
        mapDoor(p_15105_, "birch_door", 3104);
        mapDoor(p_15105_, "jungle_door", 3120);
        mapDoor(p_15105_, "acacia_door", 3136);
        mapDoor(p_15105_, "dark_oak_door", 3152);
    });
    static final Map<String, Dynamic<?>> NOTE_BLOCK_MAP = DataFixUtils.make(Maps.newHashMap(), p_15102_ -> {
        for (int i = 0; i < 26; i++) {
            p_15102_.put("true" + i, BlockStateData.parse("{Name:'minecraft:note_block',Properties:{powered:'true',note:'" + i + "'}}"));
            p_15102_.put("false" + i, BlockStateData.parse("{Name:'minecraft:note_block',Properties:{powered:'false',note:'" + i + "'}}"));
        }
    });
    private static final Int2ObjectMap<String> DYE_COLOR_MAP = DataFixUtils.make(new Int2ObjectOpenHashMap<>(), p_15070_ -> {
        p_15070_.put(0, "white");
        p_15070_.put(1, "orange");
        p_15070_.put(2, "magenta");
        p_15070_.put(3, "light_blue");
        p_15070_.put(4, "yellow");
        p_15070_.put(5, "lime");
        p_15070_.put(6, "pink");
        p_15070_.put(7, "gray");
        p_15070_.put(8, "light_gray");
        p_15070_.put(9, "cyan");
        p_15070_.put(10, "purple");
        p_15070_.put(11, "blue");
        p_15070_.put(12, "brown");
        p_15070_.put(13, "green");
        p_15070_.put(14, "red");
        p_15070_.put(15, "black");
    });
    static final Map<String, Dynamic<?>> BED_BLOCK_MAP = DataFixUtils.make(Maps.newHashMap(), p_15095_ -> {
        for (Entry<String> entry : DYE_COLOR_MAP.int2ObjectEntrySet()) {
            if (!Objects.equals(entry.getValue(), "red")) {
                addBeds(p_15095_, entry.getIntKey(), entry.getValue());
            }
        }
    });
    static final Map<String, Dynamic<?>> BANNER_BLOCK_MAP = DataFixUtils.make(Maps.newHashMap(), p_15072_ -> {
        for (Entry<String> entry : DYE_COLOR_MAP.int2ObjectEntrySet()) {
            if (!Objects.equals(entry.getValue(), "white")) {
                addBanners(p_15072_, 15 - entry.getIntKey(), entry.getValue());
            }
        }
    });
    static final Dynamic<?> AIR = BlockStateData.getTag(0);
    private static final int SIZE = 4096;

    public ChunkPalettedStorageFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    private static void mapSkull(Map<String, Dynamic<?>> map, int id, String skullType, String suffix) {
        map.put(id + "north", BlockStateData.parse("{Name:'minecraft:" + skullType + "_wall_" + suffix + "',Properties:{facing:'north'}}"));
        map.put(id + "east", BlockStateData.parse("{Name:'minecraft:" + skullType + "_wall_" + suffix + "',Properties:{facing:'east'}}"));
        map.put(id + "south", BlockStateData.parse("{Name:'minecraft:" + skullType + "_wall_" + suffix + "',Properties:{facing:'south'}}"));
        map.put(id + "west", BlockStateData.parse("{Name:'minecraft:" + skullType + "_wall_" + suffix + "',Properties:{facing:'west'}}"));

        for (int i = 0; i < 16; i++) {
            map.put("" + id + i, BlockStateData.parse("{Name:'minecraft:" + skullType + "_" + suffix + "',Properties:{rotation:'" + i + "'}}"));
        }
    }

    private static void mapDoor(Map<String, Dynamic<?>> map, String doorId, int id) {
        map.put(
            "minecraft:" + doorId + "eastlowerleftfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'lower',hinge:'left',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "eastlowerleftfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'lower',hinge:'left',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "eastlowerlefttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'lower',hinge:'left',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "eastlowerlefttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'lower',hinge:'left',open:'true',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "eastlowerrightfalsefalse", BlockStateData.getTag(id));
        map.put(
            "minecraft:" + doorId + "eastlowerrightfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'lower',hinge:'right',open:'false',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "eastlowerrighttruefalse", BlockStateData.getTag(id + 4));
        map.put(
            "minecraft:" + doorId + "eastlowerrighttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'lower',hinge:'right',open:'true',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "eastupperleftfalsefalse", BlockStateData.getTag(id + 8));
        map.put("minecraft:" + doorId + "eastupperleftfalsetrue", BlockStateData.getTag(id + 10));
        map.put(
            "minecraft:" + doorId + "eastupperlefttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'upper',hinge:'left',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "eastupperlefttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'upper',hinge:'left',open:'true',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "eastupperrightfalsefalse", BlockStateData.getTag(id + 9));
        map.put("minecraft:" + doorId + "eastupperrightfalsetrue", BlockStateData.getTag(id + 11));
        map.put(
            "minecraft:" + doorId + "eastupperrighttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'upper',hinge:'right',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "eastupperrighttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'upper',hinge:'right',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "northlowerleftfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'lower',hinge:'left',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "northlowerleftfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'lower',hinge:'left',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "northlowerlefttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'lower',hinge:'left',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "northlowerlefttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'lower',hinge:'left',open:'true',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "northlowerrightfalsefalse", BlockStateData.getTag(id + 3));
        map.put(
            "minecraft:" + doorId + "northlowerrightfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'lower',hinge:'right',open:'false',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "northlowerrighttruefalse", BlockStateData.getTag(id + 7));
        map.put(
            "minecraft:" + doorId + "northlowerrighttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'lower',hinge:'right',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "northupperleftfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'upper',hinge:'left',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "northupperleftfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'upper',hinge:'left',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "northupperlefttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'upper',hinge:'left',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "northupperlefttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'upper',hinge:'left',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "northupperrightfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'upper',hinge:'right',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "northupperrightfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'upper',hinge:'right',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "northupperrighttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'upper',hinge:'right',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "northupperrighttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'upper',hinge:'right',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "southlowerleftfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'lower',hinge:'left',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "southlowerleftfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'lower',hinge:'left',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "southlowerlefttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'lower',hinge:'left',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "southlowerlefttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'lower',hinge:'left',open:'true',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "southlowerrightfalsefalse", BlockStateData.getTag(id + 1));
        map.put(
            "minecraft:" + doorId + "southlowerrightfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'lower',hinge:'right',open:'false',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "southlowerrighttruefalse", BlockStateData.getTag(id + 5));
        map.put(
            "minecraft:" + doorId + "southlowerrighttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'lower',hinge:'right',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "southupperleftfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'upper',hinge:'left',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "southupperleftfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'upper',hinge:'left',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "southupperlefttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'upper',hinge:'left',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "southupperlefttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'upper',hinge:'left',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "southupperrightfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'upper',hinge:'right',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "southupperrightfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'upper',hinge:'right',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "southupperrighttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'upper',hinge:'right',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "southupperrighttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'upper',hinge:'right',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "westlowerleftfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'lower',hinge:'left',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "westlowerleftfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'lower',hinge:'left',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "westlowerlefttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'lower',hinge:'left',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "westlowerlefttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'lower',hinge:'left',open:'true',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "westlowerrightfalsefalse", BlockStateData.getTag(id + 2));
        map.put(
            "minecraft:" + doorId + "westlowerrightfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'lower',hinge:'right',open:'false',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "westlowerrighttruefalse", BlockStateData.getTag(id + 6));
        map.put(
            "minecraft:" + doorId + "westlowerrighttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'lower',hinge:'right',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "westupperleftfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'upper',hinge:'left',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "westupperleftfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'upper',hinge:'left',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "westupperlefttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'upper',hinge:'left',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "westupperlefttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'upper',hinge:'left',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "westupperrightfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'upper',hinge:'right',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "westupperrightfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'upper',hinge:'right',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "westupperrighttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'upper',hinge:'right',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "westupperrighttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'upper',hinge:'right',open:'true',powered:'true'}}")
        );
    }

    private static void addBeds(Map<String, Dynamic<?>> map, int id, String bedColor) {
        map.put(
            "southfalsefoot" + id,
            BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'south',occupied:'false',part:'foot'}}")
        );
        map.put(
            "westfalsefoot" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'west',occupied:'false',part:'foot'}}")
        );
        map.put(
            "northfalsefoot" + id,
            BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'north',occupied:'false',part:'foot'}}")
        );
        map.put(
            "eastfalsefoot" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'east',occupied:'false',part:'foot'}}")
        );
        map.put(
            "southfalsehead" + id,
            BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'south',occupied:'false',part:'head'}}")
        );
        map.put(
            "westfalsehead" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'west',occupied:'false',part:'head'}}")
        );
        map.put(
            "northfalsehead" + id,
            BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'north',occupied:'false',part:'head'}}")
        );
        map.put(
            "eastfalsehead" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'east',occupied:'false',part:'head'}}")
        );
        map.put(
            "southtruehead" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'south',occupied:'true',part:'head'}}")
        );
        map.put(
            "westtruehead" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'west',occupied:'true',part:'head'}}")
        );
        map.put(
            "northtruehead" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'north',occupied:'true',part:'head'}}")
        );
        map.put(
            "easttruehead" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'east',occupied:'true',part:'head'}}")
        );
    }

    private static void addBanners(Map<String, Dynamic<?>> map, int id, String bannerColor) {
        for (int i = 0; i < 16; i++) {
            map.put(i + "_" + id, BlockStateData.parse("{Name:'minecraft:" + bannerColor + "_banner',Properties:{rotation:'" + i + "'}}"));
        }

        map.put("north_" + id, BlockStateData.parse("{Name:'minecraft:" + bannerColor + "_wall_banner',Properties:{facing:'north'}}"));
        map.put("south_" + id, BlockStateData.parse("{Name:'minecraft:" + bannerColor + "_wall_banner',Properties:{facing:'south'}}"));
        map.put("west_" + id, BlockStateData.parse("{Name:'minecraft:" + bannerColor + "_wall_banner',Properties:{facing:'west'}}"));
        map.put("east_" + id, BlockStateData.parse("{Name:'minecraft:" + bannerColor + "_wall_banner',Properties:{facing:'east'}}"));
    }

    public static String getName(Dynamic<?> data) {
        return data.get("Name").asString("");
    }

    public static String getProperty(Dynamic<?> data, String key) {
        return data.get("Properties").get(key).asString("");
    }

    public static int idFor(CrudeIncrementalIntIdentityHashBiMap<Dynamic<?>> palette, Dynamic<?> data) {
        int i = palette.getId(data);
        if (i == -1) {
            i = palette.add(data);
        }

        return i;
    }

    private Dynamic<?> fix(Dynamic<?> dynamic) {
        Optional<? extends Dynamic<?>> optional = dynamic.get("Level").result();
        return optional.isPresent() && optional.get().get("Sections").asStreamOpt().result().isPresent()
            ? dynamic.set("Level", new ChunkPalettedStorageFix.UpgradeChunk((Dynamic<?>)optional.get()).write())
            : dynamic;
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        Type<?> type1 = this.getOutputSchema().getType(References.CHUNK);
        return this.writeFixAndRead("ChunkPalettedStorageFix", type, type1, this::fix);
    }

    public static int getSideMask(boolean west, boolean east, boolean north, boolean south) {
        int i = 0;
        if (north) {
            if (east) {
                i |= 2;
            } else if (west) {
                i |= 128;
            } else {
                i |= 1;
            }
        } else if (south) {
            if (west) {
                i |= 32;
            } else if (east) {
                i |= 8;
            } else {
                i |= 16;
            }
        } else if (east) {
            i |= 4;
        } else if (west) {
            i |= 64;
        }

        return i;
    }

    static {
        FIX.set(2);
        FIX.set(3);
        FIX.set(110);
        FIX.set(140);
        FIX.set(144);
        FIX.set(25);
        FIX.set(86);
        FIX.set(26);
        FIX.set(176);
        FIX.set(177);
        FIX.set(175);
        FIX.set(64);
        FIX.set(71);
        FIX.set(193);
        FIX.set(194);
        FIX.set(195);
        FIX.set(196);
        FIX.set(197);
        VIRTUAL.set(54);
        VIRTUAL.set(146);
        VIRTUAL.set(25);
        VIRTUAL.set(26);
        VIRTUAL.set(51);
        VIRTUAL.set(53);
        VIRTUAL.set(67);
        VIRTUAL.set(108);
        VIRTUAL.set(109);
        VIRTUAL.set(114);
        VIRTUAL.set(128);
        VIRTUAL.set(134);
        VIRTUAL.set(135);
        VIRTUAL.set(136);
        VIRTUAL.set(156);
        VIRTUAL.set(163);
        VIRTUAL.set(164);
        VIRTUAL.set(180);
        VIRTUAL.set(203);
        VIRTUAL.set(55);
        VIRTUAL.set(85);
        VIRTUAL.set(113);
        VIRTUAL.set(188);
        VIRTUAL.set(189);
        VIRTUAL.set(190);
        VIRTUAL.set(191);
        VIRTUAL.set(192);
        VIRTUAL.set(93);
        VIRTUAL.set(94);
        VIRTUAL.set(101);
        VIRTUAL.set(102);
        VIRTUAL.set(160);
        VIRTUAL.set(106);
        VIRTUAL.set(107);
        VIRTUAL.set(183);
        VIRTUAL.set(184);
        VIRTUAL.set(185);
        VIRTUAL.set(186);
        VIRTUAL.set(187);
        VIRTUAL.set(132);
        VIRTUAL.set(139);
        VIRTUAL.set(199);
    }

    static class DataLayer {
        private static final int SIZE = 2048;
        private static final int NIBBLE_SIZE = 4;
        private final byte[] data;

        public DataLayer() {
            this.data = new byte[2048];
        }

        public DataLayer(byte[] data) {
            this.data = data;
            if (data.length != 2048) {
                throw new IllegalArgumentException("ChunkNibbleArrays should be 2048 bytes not: " + data.length);
            }
        }

        public int get(int p_15136_, int p_15137_, int p_15138_) {
            int i = this.getPosition(p_15137_ << 8 | p_15138_ << 4 | p_15136_);
            return this.isFirst(p_15137_ << 8 | p_15138_ << 4 | p_15136_) ? this.data[i] & 15 : this.data[i] >> 4 & 15;
        }

        private boolean isFirst(int p_15134_) {
            return (p_15134_ & 1) == 0;
        }

        private int getPosition(int p_15140_) {
            return p_15140_ >> 1;
        }
    }

    public static enum Direction {
        DOWN(ChunkPalettedStorageFix.Direction.AxisDirection.NEGATIVE, ChunkPalettedStorageFix.Direction.Axis.Y),
        UP(ChunkPalettedStorageFix.Direction.AxisDirection.POSITIVE, ChunkPalettedStorageFix.Direction.Axis.Y),
        NORTH(ChunkPalettedStorageFix.Direction.AxisDirection.NEGATIVE, ChunkPalettedStorageFix.Direction.Axis.Z),
        SOUTH(ChunkPalettedStorageFix.Direction.AxisDirection.POSITIVE, ChunkPalettedStorageFix.Direction.Axis.Z),
        WEST(ChunkPalettedStorageFix.Direction.AxisDirection.NEGATIVE, ChunkPalettedStorageFix.Direction.Axis.X),
        EAST(ChunkPalettedStorageFix.Direction.AxisDirection.POSITIVE, ChunkPalettedStorageFix.Direction.Axis.X);

        private final ChunkPalettedStorageFix.Direction.Axis axis;
        private final ChunkPalettedStorageFix.Direction.AxisDirection axisDirection;

        private Direction(ChunkPalettedStorageFix.Direction.AxisDirection axisDirection, ChunkPalettedStorageFix.Direction.Axis axis) {
            this.axis = axis;
            this.axisDirection = axisDirection;
        }

        public ChunkPalettedStorageFix.Direction.AxisDirection getAxisDirection() {
            return this.axisDirection;
        }

        public ChunkPalettedStorageFix.Direction.Axis getAxis() {
            return this.axis;
        }

        public static enum Axis {
            X,
            Y,
            Z;
        }

        public static enum AxisDirection {
            POSITIVE(1),
            NEGATIVE(-1);

            private final int step;

            private AxisDirection(int step) {
                this.step = step;
            }

            public int getStep() {
                return this.step;
            }
        }
    }

    static class Section {
        private final CrudeIncrementalIntIdentityHashBiMap<Dynamic<?>> palette = CrudeIncrementalIntIdentityHashBiMap.create(32);
        private final List<Dynamic<?>> listTag;
        private final Dynamic<?> section;
        private final boolean hasData;
        final Int2ObjectMap<IntList> toFix = new Int2ObjectLinkedOpenHashMap<>();
        final IntList update = new IntArrayList();
        public final int y;
        private final Set<Dynamic<?>> seen = Sets.newIdentityHashSet();
        private final int[] buffer = new int[4096];

        public Section(Dynamic<?> section) {
            this.listTag = Lists.newArrayList();
            this.section = section;
            this.y = section.get("Y").asInt(0);
            this.hasData = section.get("Blocks").result().isPresent();
        }

        public Dynamic<?> getBlock(int id) {
            if (id >= 0 && id <= 4095) {
                Dynamic<?> dynamic = this.palette.byId(this.buffer[id]);
                return dynamic == null ? ChunkPalettedStorageFix.AIR : dynamic;
            } else {
                return ChunkPalettedStorageFix.AIR;
            }
        }

        public void setBlock(int index, Dynamic<?> data) {
            if (this.seen.add(data)) {
                this.listTag.add("%%FILTER_ME%%".equals(ChunkPalettedStorageFix.getName(data)) ? ChunkPalettedStorageFix.AIR : data);
            }

            this.buffer[index] = ChunkPalettedStorageFix.idFor(this.palette, data);
        }

        public int upgrade(int p_15210_) {
            if (!this.hasData) {
                return p_15210_;
            } else {
                ByteBuffer bytebuffer = this.section.get("Blocks").asByteBufferOpt().result().get();
                ChunkPalettedStorageFix.DataLayer chunkpalettedstoragefix$datalayer = this.section
                    .get("Data")
                    .asByteBufferOpt()
                    .map(p_15214_ -> new ChunkPalettedStorageFix.DataLayer(DataFixUtils.toArray(p_15214_)))
                    .result()
                    .orElseGet(ChunkPalettedStorageFix.DataLayer::new);
                ChunkPalettedStorageFix.DataLayer chunkpalettedstoragefix$datalayer1 = this.section
                    .get("Add")
                    .asByteBufferOpt()
                    .map(p_15208_ -> new ChunkPalettedStorageFix.DataLayer(DataFixUtils.toArray(p_15208_)))
                    .result()
                    .orElseGet(ChunkPalettedStorageFix.DataLayer::new);
                this.seen.add(ChunkPalettedStorageFix.AIR);
                ChunkPalettedStorageFix.idFor(this.palette, ChunkPalettedStorageFix.AIR);
                this.listTag.add(ChunkPalettedStorageFix.AIR);

                for (int i = 0; i < 4096; i++) {
                    int j = i & 15;
                    int k = i >> 8 & 15;
                    int l = i >> 4 & 15;
                    int i1 = chunkpalettedstoragefix$datalayer1.get(j, k, l) << 12
                        | (bytebuffer.get(i) & 255) << 4
                        | chunkpalettedstoragefix$datalayer.get(j, k, l);
                    if (ChunkPalettedStorageFix.FIX.get(i1 >> 4)) {
                        this.addFix(i1 >> 4, i);
                    }

                    if (ChunkPalettedStorageFix.VIRTUAL.get(i1 >> 4)) {
                        int j1 = ChunkPalettedStorageFix.getSideMask(j == 0, j == 15, l == 0, l == 15);
                        if (j1 == 0) {
                            this.update.add(i);
                        } else {
                            p_15210_ |= j1;
                        }
                    }

                    this.setBlock(i, BlockStateData.getTag(i1));
                }

                return p_15210_;
            }
        }

        private void addFix(int p_15200_, int p_15201_) {
            IntList intlist = this.toFix.get(p_15200_);
            if (intlist == null) {
                intlist = new IntArrayList();
                this.toFix.put(p_15200_, intlist);
            }

            intlist.add(p_15201_);
        }

        public Dynamic<?> write() {
            Dynamic<?> dynamic = this.section;
            if (!this.hasData) {
                return dynamic;
            } else {
                dynamic = dynamic.set("Palette", dynamic.createList(this.listTag.stream()));
                int i = Math.max(4, DataFixUtils.ceillog2(this.seen.size()));
                PackedBitStorage packedbitstorage = new PackedBitStorage(i, 4096);

                for (int j = 0; j < this.buffer.length; j++) {
                    packedbitstorage.set(j, this.buffer[j]);
                }

                dynamic = dynamic.set("BlockStates", dynamic.createLongList(Arrays.stream(packedbitstorage.getRaw())));
                dynamic = dynamic.remove("Blocks");
                dynamic = dynamic.remove("Data");
                return dynamic.remove("Add");
            }
        }
    }

    static final class UpgradeChunk {
        private int sides;
        private final ChunkPalettedStorageFix.Section[] sections = new ChunkPalettedStorageFix.Section[16];
        private final Dynamic<?> level;
        private final int x;
        private final int z;
        private final Int2ObjectMap<Dynamic<?>> blockEntities = new Int2ObjectLinkedOpenHashMap<>(16);

        public UpgradeChunk(Dynamic<?> level) {
            this.level = level;
            this.x = level.get("xPos").asInt(0) << 4;
            this.z = level.get("zPos").asInt(0) << 4;
            level.get("TileEntities")
                .asStreamOpt()
                .ifSuccess(
                    p_15241_ -> p_15241_.forEach(
                            p_145228_ -> {
                                int l3 = p_145228_.get("x").asInt(0) - this.x & 15;
                                int i4 = p_145228_.get("y").asInt(0);
                                int j4 = p_145228_.get("z").asInt(0) - this.z & 15;
                                int k4 = i4 << 8 | j4 << 4 | l3;
                                if (this.blockEntities.put(k4, (Dynamic<?>)p_145228_) != null) {
                                    ChunkPalettedStorageFix.LOGGER
                                        .warn("In chunk: {}x{} found a duplicate block entity at position: [{}, {}, {}]", this.x, this.z, l3, i4, j4);
                                }
                            }
                        )
                );
            boolean flag = level.get("convertedFromAlphaFormat").asBoolean(false);
            level.get("Sections").asStreamOpt().ifSuccess(p_15235_ -> p_15235_.forEach(p_145226_ -> {
                    ChunkPalettedStorageFix.Section chunkpalettedstoragefix$section1 = new ChunkPalettedStorageFix.Section((Dynamic<?>)p_145226_);
                    this.sides = chunkpalettedstoragefix$section1.upgrade(this.sides);
                    this.sections[chunkpalettedstoragefix$section1.y] = chunkpalettedstoragefix$section1;
                }));

            for (ChunkPalettedStorageFix.Section chunkpalettedstoragefix$section : this.sections) {
                if (chunkpalettedstoragefix$section != null) {
                    for (java.util.Map.Entry<Integer, IntList> entry : chunkpalettedstoragefix$section.toFix.entrySet()) {
                        int i = chunkpalettedstoragefix$section.y << 12;
                        switch (entry.getKey()) {
                            case 2:
                                for (int i3 : entry.getValue()) {
                                    i3 |= i;
                                    Dynamic<?> dynamic11 = this.getBlock(i3);
                                    if ("minecraft:grass_block".equals(ChunkPalettedStorageFix.getName(dynamic11))) {
                                        String s12 = ChunkPalettedStorageFix.getName(this.getBlock(relative(i3, ChunkPalettedStorageFix.Direction.UP)));
                                        if ("minecraft:snow".equals(s12) || "minecraft:snow_layer".equals(s12)) {
                                            this.setBlock(i3, ChunkPalettedStorageFix.SNOWY_GRASS);
                                        }
                                    }
                                }
                                break;
                            case 3:
                                for (int l2 : entry.getValue()) {
                                    l2 |= i;
                                    Dynamic<?> dynamic10 = this.getBlock(l2);
                                    if ("minecraft:podzol".equals(ChunkPalettedStorageFix.getName(dynamic10))) {
                                        String s11 = ChunkPalettedStorageFix.getName(this.getBlock(relative(l2, ChunkPalettedStorageFix.Direction.UP)));
                                        if ("minecraft:snow".equals(s11) || "minecraft:snow_layer".equals(s11)) {
                                            this.setBlock(l2, ChunkPalettedStorageFix.SNOWY_PODZOL);
                                        }
                                    }
                                }
                                break;
                            case 25:
                                for (int k2 : entry.getValue()) {
                                    k2 |= i;
                                    Dynamic<?> dynamic9 = this.removeBlockEntity(k2);
                                    if (dynamic9 != null) {
                                        String s10 = Boolean.toString(dynamic9.get("powered").asBoolean(false))
                                            + (byte)Math.min(Math.max(dynamic9.get("note").asInt(0), 0), 24);
                                        this.setBlock(
                                            k2, ChunkPalettedStorageFix.NOTE_BLOCK_MAP.getOrDefault(s10, ChunkPalettedStorageFix.NOTE_BLOCK_MAP.get("false0"))
                                        );
                                    }
                                }
                                break;
                            case 26:
                                for (int j2 : entry.getValue()) {
                                    j2 |= i;
                                    Dynamic<?> dynamic8 = this.getBlockEntity(j2);
                                    Dynamic<?> dynamic14 = this.getBlock(j2);
                                    if (dynamic8 != null) {
                                        int k3 = dynamic8.get("color").asInt(0);
                                        if (k3 != 14 && k3 >= 0 && k3 < 16) {
                                            String s16 = ChunkPalettedStorageFix.getProperty(dynamic14, "facing")
                                                + ChunkPalettedStorageFix.getProperty(dynamic14, "occupied")
                                                + ChunkPalettedStorageFix.getProperty(dynamic14, "part")
                                                + k3;
                                            if (ChunkPalettedStorageFix.BED_BLOCK_MAP.containsKey(s16)) {
                                                this.setBlock(j2, ChunkPalettedStorageFix.BED_BLOCK_MAP.get(s16));
                                            }
                                        }
                                    }
                                }
                                break;
                            case 64:
                            case 71:
                            case 193:
                            case 194:
                            case 195:
                            case 196:
                            case 197:
                                for (int i2 : entry.getValue()) {
                                    i2 |= i;
                                    Dynamic<?> dynamic7 = this.getBlock(i2);
                                    if (ChunkPalettedStorageFix.getName(dynamic7).endsWith("_door")) {
                                        Dynamic<?> dynamic13 = this.getBlock(i2);
                                        if ("lower".equals(ChunkPalettedStorageFix.getProperty(dynamic13, "half"))) {
                                            int j3 = relative(i2, ChunkPalettedStorageFix.Direction.UP);
                                            Dynamic<?> dynamic15 = this.getBlock(j3);
                                            String s1 = ChunkPalettedStorageFix.getName(dynamic13);
                                            if (s1.equals(ChunkPalettedStorageFix.getName(dynamic15))) {
                                                String s2 = ChunkPalettedStorageFix.getProperty(dynamic13, "facing");
                                                String s3 = ChunkPalettedStorageFix.getProperty(dynamic13, "open");
                                                String s4 = flag ? "left" : ChunkPalettedStorageFix.getProperty(dynamic15, "hinge");
                                                String s5 = flag ? "false" : ChunkPalettedStorageFix.getProperty(dynamic15, "powered");
                                                this.setBlock(i2, ChunkPalettedStorageFix.DOOR_MAP.get(s1 + s2 + "lower" + s4 + s3 + s5));
                                                this.setBlock(j3, ChunkPalettedStorageFix.DOOR_MAP.get(s1 + s2 + "upper" + s4 + s3 + s5));
                                            }
                                        }
                                    }
                                }
                                break;
                            case 86:
                                for (int l1 : entry.getValue()) {
                                    l1 |= i;
                                    Dynamic<?> dynamic6 = this.getBlock(l1);
                                    if ("minecraft:carved_pumpkin".equals(ChunkPalettedStorageFix.getName(dynamic6))) {
                                        String s9 = ChunkPalettedStorageFix.getName(this.getBlock(relative(l1, ChunkPalettedStorageFix.Direction.DOWN)));
                                        if ("minecraft:grass_block".equals(s9) || "minecraft:dirt".equals(s9)) {
                                            this.setBlock(l1, ChunkPalettedStorageFix.PUMPKIN);
                                        }
                                    }
                                }
                                break;
                            case 110:
                                for (int k1 : entry.getValue()) {
                                    k1 |= i;
                                    Dynamic<?> dynamic5 = this.getBlock(k1);
                                    if ("minecraft:mycelium".equals(ChunkPalettedStorageFix.getName(dynamic5))) {
                                        String s8 = ChunkPalettedStorageFix.getName(this.getBlock(relative(k1, ChunkPalettedStorageFix.Direction.UP)));
                                        if ("minecraft:snow".equals(s8) || "minecraft:snow_layer".equals(s8)) {
                                            this.setBlock(k1, ChunkPalettedStorageFix.SNOWY_MYCELIUM);
                                        }
                                    }
                                }
                                break;
                            case 140:
                                for (int j1 : entry.getValue()) {
                                    j1 |= i;
                                    Dynamic<?> dynamic4 = this.removeBlockEntity(j1);
                                    if (dynamic4 != null) {
                                        String s7 = dynamic4.get("Item").asString("") + dynamic4.get("Data").asInt(0);
                                        this.setBlock(
                                            j1,
                                            ChunkPalettedStorageFix.FLOWER_POT_MAP
                                                .getOrDefault(s7, ChunkPalettedStorageFix.FLOWER_POT_MAP.get("minecraft:air0"))
                                        );
                                    }
                                }
                                break;
                            case 144:
                                for (int i1 : entry.getValue()) {
                                    i1 |= i;
                                    Dynamic<?> dynamic3 = this.getBlockEntity(i1);
                                    if (dynamic3 != null) {
                                        String s6 = String.valueOf(dynamic3.get("SkullType").asInt(0));
                                        String s14 = ChunkPalettedStorageFix.getProperty(this.getBlock(i1), "facing");
                                        String s15;
                                        if (!"up".equals(s14) && !"down".equals(s14)) {
                                            s15 = s6 + s14;
                                        } else {
                                            s15 = s6 + dynamic3.get("Rot").asInt(0);
                                        }

                                        dynamic3.remove("SkullType");
                                        dynamic3.remove("facing");
                                        dynamic3.remove("Rot");
                                        this.setBlock(i1, ChunkPalettedStorageFix.SKULL_MAP.getOrDefault(s15, ChunkPalettedStorageFix.SKULL_MAP.get("0north")));
                                    }
                                }
                                break;
                            case 175:
                                for (int l : entry.getValue()) {
                                    l |= i;
                                    Dynamic<?> dynamic2 = this.getBlock(l);
                                    if ("upper".equals(ChunkPalettedStorageFix.getProperty(dynamic2, "half"))) {
                                        Dynamic<?> dynamic12 = this.getBlock(relative(l, ChunkPalettedStorageFix.Direction.DOWN));
                                        String s13 = ChunkPalettedStorageFix.getName(dynamic12);
                                        if ("minecraft:sunflower".equals(s13)) {
                                            this.setBlock(l, ChunkPalettedStorageFix.UPPER_SUNFLOWER);
                                        } else if ("minecraft:lilac".equals(s13)) {
                                            this.setBlock(l, ChunkPalettedStorageFix.UPPER_LILAC);
                                        } else if ("minecraft:tall_grass".equals(s13)) {
                                            this.setBlock(l, ChunkPalettedStorageFix.UPPER_TALL_GRASS);
                                        } else if ("minecraft:large_fern".equals(s13)) {
                                            this.setBlock(l, ChunkPalettedStorageFix.UPPER_LARGE_FERN);
                                        } else if ("minecraft:rose_bush".equals(s13)) {
                                            this.setBlock(l, ChunkPalettedStorageFix.UPPER_ROSE_BUSH);
                                        } else if ("minecraft:peony".equals(s13)) {
                                            this.setBlock(l, ChunkPalettedStorageFix.UPPER_PEONY);
                                        }
                                    }
                                }
                                break;
                            case 176:
                            case 177:
                                for (int j : entry.getValue()) {
                                    j |= i;
                                    Dynamic<?> dynamic = this.getBlockEntity(j);
                                    Dynamic<?> dynamic1 = this.getBlock(j);
                                    if (dynamic != null) {
                                        int k = dynamic.get("Base").asInt(0);
                                        if (k != 15 && k >= 0 && k < 16) {
                                            String s = ChunkPalettedStorageFix.getProperty(dynamic1, entry.getKey() == 176 ? "rotation" : "facing") + "_" + k;
                                            if (ChunkPalettedStorageFix.BANNER_BLOCK_MAP.containsKey(s)) {
                                                this.setBlock(j, ChunkPalettedStorageFix.BANNER_BLOCK_MAP.get(s));
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }

        @Nullable
        private Dynamic<?> getBlockEntity(int index) {
            return this.blockEntities.get(index);
        }

        @Nullable
        private Dynamic<?> removeBlockEntity(int index) {
            return this.blockEntities.remove(index);
        }

        public static int relative(int p_15227_, ChunkPalettedStorageFix.Direction direction) {
            switch (direction.getAxis()) {
                case X:
                    int i = (p_15227_ & 15) + direction.getAxisDirection().getStep();
                    return i >= 0 && i <= 15 ? p_15227_ & -16 | i : -1;
                case Y:
                    int j = (p_15227_ >> 8) + direction.getAxisDirection().getStep();
                    return j >= 0 && j <= 255 ? p_15227_ & 0xFF | j << 8 : -1;
                case Z:
                    int k = (p_15227_ >> 4 & 15) + direction.getAxisDirection().getStep();
                    return k >= 0 && k <= 15 ? p_15227_ & -241 | k << 4 : -1;
                default:
                    return -1;
            }
        }

        private void setBlock(int p_15230_, Dynamic<?> dynamic) {
            if (p_15230_ >= 0 && p_15230_ <= 65535) {
                ChunkPalettedStorageFix.Section chunkpalettedstoragefix$section = this.getSection(p_15230_);
                if (chunkpalettedstoragefix$section != null) {
                    chunkpalettedstoragefix$section.setBlock(p_15230_ & 4095, dynamic);
                }
            }
        }

        @Nullable
        private ChunkPalettedStorageFix.Section getSection(int p_15245_) {
            int i = p_15245_ >> 12;
            return i < this.sections.length ? this.sections[i] : null;
        }

        public Dynamic<?> getBlock(int p_15225_) {
            if (p_15225_ >= 0 && p_15225_ <= 65535) {
                ChunkPalettedStorageFix.Section chunkpalettedstoragefix$section = this.getSection(p_15225_);
                return chunkpalettedstoragefix$section == null ? ChunkPalettedStorageFix.AIR : chunkpalettedstoragefix$section.getBlock(p_15225_ & 4095);
            } else {
                return ChunkPalettedStorageFix.AIR;
            }
        }

        public Dynamic<?> write() {
            Dynamic<?> dynamic = this.level;
            if (this.blockEntities.isEmpty()) {
                dynamic = dynamic.remove("TileEntities");
            } else {
                dynamic = dynamic.set("TileEntities", dynamic.createList(this.blockEntities.values().stream()));
            }

            Dynamic<?> dynamic1 = dynamic.emptyMap();
            List<Dynamic<?>> list = Lists.newArrayList();

            for (ChunkPalettedStorageFix.Section chunkpalettedstoragefix$section : this.sections) {
                if (chunkpalettedstoragefix$section != null) {
                    list.add(chunkpalettedstoragefix$section.write());
                    dynamic1 = dynamic1.set(
                        String.valueOf(chunkpalettedstoragefix$section.y),
                        dynamic1.createIntList(Arrays.stream(chunkpalettedstoragefix$section.update.toIntArray()))
                    );
                }
            }

            Dynamic<?> dynamic2 = dynamic.emptyMap();
            dynamic2 = dynamic2.set("Sides", dynamic2.createByte((byte)this.sides));
            dynamic2 = dynamic2.set("Indices", dynamic1);
            return dynamic.set("UpgradeData", dynamic2).set("Sections", dynamic2.createList(list.stream()));
        }
    }
}
