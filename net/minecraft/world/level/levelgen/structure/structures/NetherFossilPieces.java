package net.minecraft.world.level.levelgen.structure.structures;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class NetherFossilPieces {
    private static final ResourceLocation[] FOSSILS = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("nether_fossils/fossil_1"),
        ResourceLocation.withDefaultNamespace("nether_fossils/fossil_2"),
        ResourceLocation.withDefaultNamespace("nether_fossils/fossil_3"),
        ResourceLocation.withDefaultNamespace("nether_fossils/fossil_4"),
        ResourceLocation.withDefaultNamespace("nether_fossils/fossil_5"),
        ResourceLocation.withDefaultNamespace("nether_fossils/fossil_6"),
        ResourceLocation.withDefaultNamespace("nether_fossils/fossil_7"),
        ResourceLocation.withDefaultNamespace("nether_fossils/fossil_8"),
        ResourceLocation.withDefaultNamespace("nether_fossils/fossil_9"),
        ResourceLocation.withDefaultNamespace("nether_fossils/fossil_10"),
        ResourceLocation.withDefaultNamespace("nether_fossils/fossil_11"),
        ResourceLocation.withDefaultNamespace("nether_fossils/fossil_12"),
        ResourceLocation.withDefaultNamespace("nether_fossils/fossil_13"),
        ResourceLocation.withDefaultNamespace("nether_fossils/fossil_14")
    };

    public static void addPieces(StructureTemplateManager structureManager, StructurePieceAccessor pieces, RandomSource random, BlockPos pos) {
        Rotation rotation = Rotation.getRandom(random);
        pieces.addPiece(new NetherFossilPieces.NetherFossilPiece(structureManager, Util.getRandom(FOSSILS, random), pos, rotation));
    }

    public static class NetherFossilPiece extends TemplateStructurePiece {
        public NetherFossilPiece(StructureTemplateManager structureManager, ResourceLocation location, BlockPos pos, Rotation rotation) {
            super(StructurePieceType.NETHER_FOSSIL, 0, structureManager, location, location.toString(), makeSettings(rotation), pos);
        }

        public NetherFossilPiece(StructureTemplateManager structureManager, CompoundTag tag) {
            super(StructurePieceType.NETHER_FOSSIL, tag, structureManager, p_228568_ -> makeSettings(Rotation.valueOf(tag.getString("Rot"))));
        }

        private static StructurePlaceSettings makeSettings(Rotation rotation) {
            return new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE).addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putString("Rot", this.placeSettings.getRotation().name());
        }

        @Override
        protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
        }

        @Override
        public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox box,
            ChunkPos chunkPos,
            BlockPos pos
        ) {
            box.encapsulate(this.template.getBoundingBox(this.placeSettings, this.templatePosition));
            super.postProcess(level, structureManager, generator, random, box, chunkPos, pos);
        }
    }
}
