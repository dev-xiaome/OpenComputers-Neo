package net.minecraft.client.color.block;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BlockColors {
    private static final int DEFAULT = -1;
    // Neo: Use the block instance directly as non-Vanilla block ids are not constant
    private final java.util.Map<Block, BlockColor> blockColors = new java.util.IdentityHashMap<>();
    private final Map<Block, Set<Property<?>>> coloringStates = Maps.newHashMap();

    public static BlockColors createDefault() {
        BlockColors blockcolors = new BlockColors();
        blockcolors.register(
            (p_276233_, p_276234_, p_276235_, p_276236_) -> p_276234_ != null && p_276235_ != null
                    ? BiomeColors.getAverageGrassColor(
                        p_276234_, p_276233_.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER ? p_276235_.below() : p_276235_
                    )
                    : GrassColor.getDefaultColor(),
            Blocks.LARGE_FERN,
            Blocks.TALL_GRASS
        );
        blockcolors.addColoringState(DoublePlantBlock.HALF, Blocks.LARGE_FERN, Blocks.TALL_GRASS);
        blockcolors.register(
            (p_276237_, p_276238_, p_276239_, p_276240_) -> p_276238_ != null && p_276239_ != null
                    ? BiomeColors.getAverageGrassColor(p_276238_, p_276239_)
                    : GrassColor.getDefaultColor(),
            Blocks.GRASS_BLOCK,
            Blocks.FERN,
            Blocks.SHORT_GRASS,
            Blocks.POTTED_FERN
        );
        blockcolors.register((p_276241_, p_276242_, p_276243_, p_276244_) -> {
            if (p_276244_ != 0) {
                return p_276242_ != null && p_276243_ != null ? BiomeColors.getAverageGrassColor(p_276242_, p_276243_) : GrassColor.getDefaultColor();
            } else {
                return -1;
            }
        }, Blocks.PINK_PETALS);
        blockcolors.register((p_92636_, p_92637_, p_92638_, p_92639_) -> FoliageColor.getEvergreenColor(), Blocks.SPRUCE_LEAVES);
        blockcolors.register((p_92631_, p_92632_, p_92633_, p_92634_) -> FoliageColor.getBirchColor(), Blocks.BIRCH_LEAVES);
        blockcolors.register(
            (p_92626_, p_92627_, p_92628_, p_92629_) -> p_92627_ != null && p_92628_ != null
                    ? BiomeColors.getAverageFoliageColor(p_92627_, p_92628_)
                    : FoliageColor.getDefaultColor(),
            Blocks.OAK_LEAVES,
            Blocks.JUNGLE_LEAVES,
            Blocks.ACACIA_LEAVES,
            Blocks.DARK_OAK_LEAVES,
            Blocks.VINE,
            Blocks.MANGROVE_LEAVES
        );
        blockcolors.register(
            (p_92621_, p_92622_, p_92623_, p_92624_) -> p_92622_ != null && p_92623_ != null ? BiomeColors.getAverageWaterColor(p_92622_, p_92623_) : -1,
            Blocks.WATER,
            Blocks.BUBBLE_COLUMN,
            Blocks.WATER_CAULDRON
        );
        blockcolors.register(
            (p_92616_, p_92617_, p_92618_, p_92619_) -> RedStoneWireBlock.getColorForPower(p_92616_.getValue(RedStoneWireBlock.POWER)), Blocks.REDSTONE_WIRE
        );
        blockcolors.addColoringState(RedStoneWireBlock.POWER, Blocks.REDSTONE_WIRE);
        blockcolors.register(
            (p_92611_, p_92612_, p_92613_, p_92614_) -> p_92612_ != null && p_92613_ != null ? BiomeColors.getAverageGrassColor(p_92612_, p_92613_) : -1,
            Blocks.SUGAR_CANE
        );
        blockcolors.register((p_92606_, p_92607_, p_92608_, p_92609_) -> -2046180, Blocks.ATTACHED_MELON_STEM, Blocks.ATTACHED_PUMPKIN_STEM);
        blockcolors.register((p_329695_, p_329696_, p_329697_, p_329698_) -> {
            int i = p_329695_.getValue(StemBlock.AGE);
            return FastColor.ARGB32.color(i * 32, 255 - i * 8, i * 4);
        }, Blocks.MELON_STEM, Blocks.PUMPKIN_STEM);
        blockcolors.addColoringState(StemBlock.AGE, Blocks.MELON_STEM, Blocks.PUMPKIN_STEM);
        blockcolors.register((p_92596_, p_92597_, p_92598_, p_92599_) -> p_92597_ != null && p_92598_ != null ? -14647248 : -9321636, Blocks.LILY_PAD);
        net.neoforged.neoforge.client.ClientHooks.onBlockColorsInit(blockcolors);
        return blockcolors;
    }

    public int getColor(BlockState state, Level level, BlockPos pos) {
        BlockColor blockcolor = this.blockColors.get(state.getBlock());
        if (blockcolor != null) {
            return blockcolor.getColor(state, null, null, 0);
        } else {
            MapColor mapcolor = state.getMapColor(level, pos);
            return mapcolor != null ? mapcolor.col : -1;
        }
    }

    public int getColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        BlockColor blockcolor = this.blockColors.get(state.getBlock());
        return blockcolor == null ? -1 : blockcolor.getColor(state, level, pos, tintIndex);
    }

    /**
 * @deprecated Register via {@link
 *             net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block
 *             }
 */
    @Deprecated
    public void register(BlockColor blockColor, Block... blocks) {
        for (Block block : blocks) {
            this.blockColors.put(block, blockColor);
        }
    }

    private void addColoringStates(Set<Property<?>> properties, Block... blocks) {
        for (Block block : blocks) {
            this.coloringStates.put(block, properties);
        }
    }

    private void addColoringState(Property<?> property, Block... blocks) {
        this.addColoringStates(ImmutableSet.of(property), blocks);
    }

    public Set<Property<?>> getColoringProperties(Block block) {
        return this.coloringStates.getOrDefault(block, ImmutableSet.of());
    }
}
