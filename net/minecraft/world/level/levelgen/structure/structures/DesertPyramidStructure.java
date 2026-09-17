package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.SinglePieceStructure;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class DesertPyramidStructure extends SinglePieceStructure {
    public static final MapCodec<DesertPyramidStructure> CODEC = simpleCodec(DesertPyramidStructure::new);

    public DesertPyramidStructure(Structure.StructureSettings p_227418_) {
        super(DesertPyramidPiece::new, 21, 21, p_227418_);
    }

    @Override
    public void afterPlace(
        WorldGenLevel level,
        StructureManager structureManager,
        ChunkGenerator chunkGenerator,
        RandomSource random,
        BoundingBox boundingBox,
        ChunkPos chunkPos,
        PiecesContainer pieces
    ) {
        Set<BlockPos> set = SortedArraySet.create(Vec3i::compareTo);

        for (StructurePiece structurepiece : pieces.pieces()) {
            if (structurepiece instanceof DesertPyramidPiece desertpyramidpiece) {
                set.addAll(desertpyramidpiece.getPotentialSuspiciousSandWorldPositions());
                placeSuspiciousSand(boundingBox, level, desertpyramidpiece.getRandomCollapsedRoofPos());
            }
        }

        ObjectArrayList<BlockPos> objectarraylist = new ObjectArrayList<>(set.stream().toList());
        RandomSource randomsource = RandomSource.create(level.getSeed()).forkPositional().at(pieces.calculateBoundingBox().getCenter());
        Util.shuffle(objectarraylist, randomsource);
        int i = Math.min(set.size(), randomsource.nextInt(5, 8));

        for (BlockPos blockpos : objectarraylist) {
            if (i > 0) {
                i--;
                placeSuspiciousSand(boundingBox, level, blockpos);
            } else if (boundingBox.isInside(blockpos)) {
                level.setBlock(blockpos, Blocks.SAND.defaultBlockState(), 2);
            }
        }
    }

    private static void placeSuspiciousSand(BoundingBox boundingBox, WorldGenLevel worldGenLevel, BlockPos pos) {
        if (boundingBox.isInside(pos)) {
            worldGenLevel.setBlock(pos, Blocks.SUSPICIOUS_SAND.defaultBlockState(), 2);
            worldGenLevel.getBlockEntity(pos, BlockEntityType.BRUSHABLE_BLOCK)
                .ifPresent(p_335309_ -> p_335309_.setLootTable(BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY, pos.asLong()));
        }
    }

    @Override
    public StructureType<?> type() {
        return StructureType.DESERT_PYRAMID;
    }
}
