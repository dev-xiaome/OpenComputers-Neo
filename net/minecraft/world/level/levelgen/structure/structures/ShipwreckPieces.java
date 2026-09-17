package net.minecraft.world.level.levelgen.structure.structures;

import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

public class ShipwreckPieces {
    private static final int NUMBER_OF_BLOCKS_ALLOWED_IN_WORLD_GEN_REGION = 32;
    static final BlockPos PIVOT = new BlockPos(4, 0, 15);
    private static final ResourceLocation[] STRUCTURE_LOCATION_BEACHED = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("shipwreck/with_mast"),
        ResourceLocation.withDefaultNamespace("shipwreck/sideways_full"),
        ResourceLocation.withDefaultNamespace("shipwreck/sideways_fronthalf"),
        ResourceLocation.withDefaultNamespace("shipwreck/sideways_backhalf"),
        ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_full"),
        ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_fronthalf"),
        ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_backhalf"),
        ResourceLocation.withDefaultNamespace("shipwreck/with_mast_degraded"),
        ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_full_degraded"),
        ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_fronthalf_degraded"),
        ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_backhalf_degraded")
    };
    private static final ResourceLocation[] STRUCTURE_LOCATION_OCEAN = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("shipwreck/with_mast"),
        ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_full"),
        ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_fronthalf"),
        ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_backhalf"),
        ResourceLocation.withDefaultNamespace("shipwreck/sideways_full"),
        ResourceLocation.withDefaultNamespace("shipwreck/sideways_fronthalf"),
        ResourceLocation.withDefaultNamespace("shipwreck/sideways_backhalf"),
        ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_full"),
        ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_fronthalf"),
        ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_backhalf"),
        ResourceLocation.withDefaultNamespace("shipwreck/with_mast_degraded"),
        ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_full_degraded"),
        ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_fronthalf_degraded"),
        ResourceLocation.withDefaultNamespace("shipwreck/upsidedown_backhalf_degraded"),
        ResourceLocation.withDefaultNamespace("shipwreck/sideways_full_degraded"),
        ResourceLocation.withDefaultNamespace("shipwreck/sideways_fronthalf_degraded"),
        ResourceLocation.withDefaultNamespace("shipwreck/sideways_backhalf_degraded"),
        ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_full_degraded"),
        ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_fronthalf_degraded"),
        ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_backhalf_degraded")
    };
    static final Map<String, ResourceKey<LootTable>> MARKERS_TO_LOOT = Map.of(
        "map_chest",
        BuiltInLootTables.SHIPWRECK_MAP,
        "treasure_chest",
        BuiltInLootTables.SHIPWRECK_TREASURE,
        "supply_chest",
        BuiltInLootTables.SHIPWRECK_SUPPLY
    );

    public static ShipwreckPieces.ShipwreckPiece addRandomPiece(
        StructureTemplateManager structureTemplateManager, BlockPos pos, Rotation rotation, StructurePieceAccessor pieces, RandomSource random, boolean isBeached
    ) {
        ResourceLocation resourcelocation = Util.getRandom(isBeached ? STRUCTURE_LOCATION_BEACHED : STRUCTURE_LOCATION_OCEAN, random);
        ShipwreckPieces.ShipwreckPiece shipwreckpieces$shipwreckpiece = new ShipwreckPieces.ShipwreckPiece(
            structureTemplateManager, resourcelocation, pos, rotation, isBeached
        );
        pieces.addPiece(shipwreckpieces$shipwreckpiece);
        return shipwreckpieces$shipwreckpiece;
    }

    public static class ShipwreckPiece extends TemplateStructurePiece {
        private final boolean isBeached;

        public ShipwreckPiece(StructureTemplateManager structureTemplateManager, ResourceLocation location, BlockPos pos, Rotation rotation, boolean isBeached) {
            super(StructurePieceType.SHIPWRECK_PIECE, 0, structureTemplateManager, location, location.toString(), makeSettings(rotation), pos);
            this.isBeached = isBeached;
        }

        public ShipwreckPiece(StructureTemplateManager structureTemplateManager, CompoundTag tag) {
            super(StructurePieceType.SHIPWRECK_PIECE, tag, structureTemplateManager, p_229383_ -> makeSettings(Rotation.valueOf(tag.getString("Rot"))));
            this.isBeached = tag.getBoolean("isBeached");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("isBeached", this.isBeached);
            tag.putString("Rot", this.placeSettings.getRotation().name());
        }

        private static StructurePlaceSettings makeSettings(Rotation rotation) {
            return new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setRotationPivot(ShipwreckPieces.PIVOT)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
        }

        @Override
        protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
            ResourceKey<LootTable> resourcekey = ShipwreckPieces.MARKERS_TO_LOOT.get(name);
            if (resourcekey != null) {
                RandomizableContainer.setBlockEntityLootTable(level, random, pos.below(), resourcekey);
            }
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
            if (this.isTooBigToFitInWorldGenRegion()) {
                super.postProcess(level, structureManager, generator, random, box, chunkPos, pos);
            } else {
                int i = level.getMaxBuildHeight();
                int j = 0;
                Vec3i vec3i = this.template.getSize();
                Heightmap.Types heightmap$types = this.isBeached ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.OCEAN_FLOOR_WG;
                int k = vec3i.getX() * vec3i.getZ();
                if (k == 0) {
                    j = level.getHeight(heightmap$types, this.templatePosition.getX(), this.templatePosition.getZ());
                } else {
                    BlockPos blockpos = this.templatePosition.offset(vec3i.getX() - 1, 0, vec3i.getZ() - 1);

                    for (BlockPos blockpos1 : BlockPos.betweenClosed(this.templatePosition, blockpos)) {
                        int l = level.getHeight(heightmap$types, blockpos1.getX(), blockpos1.getZ());
                        j += l;
                        i = Math.min(i, l);
                    }

                    j /= k;
                }

                this.adjustPositionHeight(this.isBeached ? this.calculateBeachedPosition(i, random) : j);
                super.postProcess(level, structureManager, generator, random, box, chunkPos, pos);
            }
        }

        public boolean isTooBigToFitInWorldGenRegion() {
            Vec3i vec3i = this.template.getSize();
            return vec3i.getX() > 32 || vec3i.getY() > 32;
        }

        public int calculateBeachedPosition(int maxHeight, RandomSource random) {
            return maxHeight - this.template.getSize().getY() / 2 - random.nextInt(3);
        }

        public void adjustPositionHeight(int height) {
            this.templatePosition = new BlockPos(this.templatePosition.getX(), height, this.templatePosition.getZ());
        }
    }
}
