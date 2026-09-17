package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.CappedProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.PosAlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.AppendLoot;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

public class OceanRuinPieces {
    static final StructureProcessor WARM_SUSPICIOUS_BLOCK_PROCESSOR = archyRuleProcessor(
        Blocks.SAND, Blocks.SUSPICIOUS_SAND, BuiltInLootTables.OCEAN_RUIN_WARM_ARCHAEOLOGY
    );
    static final StructureProcessor COLD_SUSPICIOUS_BLOCK_PROCESSOR = archyRuleProcessor(
        Blocks.GRAVEL, Blocks.SUSPICIOUS_GRAVEL, BuiltInLootTables.OCEAN_RUIN_COLD_ARCHAEOLOGY
    );
    private static final ResourceLocation[] WARM_RUINS = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("underwater_ruin/warm_1"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/warm_2"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/warm_3"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/warm_4"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/warm_5"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/warm_6"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/warm_7"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/warm_8")
    };
    private static final ResourceLocation[] RUINS_BRICK = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("underwater_ruin/brick_1"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/brick_2"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/brick_3"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/brick_4"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/brick_5"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/brick_6"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/brick_7"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/brick_8")
    };
    private static final ResourceLocation[] RUINS_CRACKED = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("underwater_ruin/cracked_1"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/cracked_2"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/cracked_3"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/cracked_4"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/cracked_5"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/cracked_6"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/cracked_7"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/cracked_8")
    };
    private static final ResourceLocation[] RUINS_MOSSY = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("underwater_ruin/mossy_1"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/mossy_2"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/mossy_3"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/mossy_4"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/mossy_5"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/mossy_6"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/mossy_7"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/mossy_8")
    };
    private static final ResourceLocation[] BIG_RUINS_BRICK = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("underwater_ruin/big_brick_1"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/big_brick_2"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/big_brick_3"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/big_brick_8")
    };
    private static final ResourceLocation[] BIG_RUINS_MOSSY = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("underwater_ruin/big_mossy_1"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/big_mossy_2"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/big_mossy_3"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/big_mossy_8")
    };
    private static final ResourceLocation[] BIG_RUINS_CRACKED = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("underwater_ruin/big_cracked_1"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/big_cracked_2"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/big_cracked_3"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/big_cracked_8")
    };
    private static final ResourceLocation[] BIG_WARM_RUINS = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("underwater_ruin/big_warm_4"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/big_warm_5"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/big_warm_6"),
        ResourceLocation.withDefaultNamespace("underwater_ruin/big_warm_7")
    };

    private static StructureProcessor archyRuleProcessor(Block block, Block suspiciousBlock, ResourceKey<LootTable> lootTable) {
        return new CappedProcessor(
            new RuleProcessor(
                List.of(
                    new ProcessorRule(
                        new BlockMatchTest(block),
                        AlwaysTrueTest.INSTANCE,
                        PosAlwaysTrueTest.INSTANCE,
                        suspiciousBlock.defaultBlockState(),
                        new AppendLoot(lootTable)
                    )
                )
            ),
            ConstantInt.of(5)
        );
    }

    private static ResourceLocation getSmallWarmRuin(RandomSource random) {
        return Util.getRandom(WARM_RUINS, random);
    }

    private static ResourceLocation getBigWarmRuin(RandomSource random) {
        return Util.getRandom(BIG_WARM_RUINS, random);
    }

    public static void addPieces(
        StructureTemplateManager structureTemplateManager,
        BlockPos pos,
        Rotation rotation,
        StructurePieceAccessor structurePieceAccessor,
        RandomSource random,
        OceanRuinStructure structure
    ) {
        boolean flag = random.nextFloat() <= structure.largeProbability;
        float f = flag ? 0.9F : 0.8F;
        addPiece(structureTemplateManager, pos, rotation, structurePieceAccessor, random, structure, flag, f);
        if (flag && random.nextFloat() <= structure.clusterProbability) {
            addClusterRuins(structureTemplateManager, random, rotation, pos, structure, structurePieceAccessor);
        }
    }

    private static void addClusterRuins(
        StructureTemplateManager structureTemplateManager,
        RandomSource random,
        Rotation p_rotation,
        BlockPos pos,
        OceanRuinStructure structure,
        StructurePieceAccessor structurePieceAccessor
    ) {
        BlockPos blockpos = new BlockPos(pos.getX(), 90, pos.getZ());
        BlockPos blockpos1 = StructureTemplate.transform(new BlockPos(15, 0, 15), Mirror.NONE, p_rotation, BlockPos.ZERO).offset(blockpos);
        BoundingBox boundingbox = BoundingBox.fromCorners(blockpos, blockpos1);
        BlockPos blockpos2 = new BlockPos(Math.min(blockpos.getX(), blockpos1.getX()), blockpos.getY(), Math.min(blockpos.getZ(), blockpos1.getZ()));
        List<BlockPos> list = allPositions(random, blockpos2);
        int i = Mth.nextInt(random, 4, 8);

        for (int j = 0; j < i; j++) {
            if (!list.isEmpty()) {
                int k = random.nextInt(list.size());
                BlockPos blockpos3 = list.remove(k);
                Rotation rotation = Rotation.getRandom(random);
                BlockPos blockpos4 = StructureTemplate.transform(new BlockPos(5, 0, 6), Mirror.NONE, rotation, BlockPos.ZERO).offset(blockpos3);
                BoundingBox boundingbox1 = BoundingBox.fromCorners(blockpos3, blockpos4);
                if (!boundingbox1.intersects(boundingbox)) {
                    addPiece(structureTemplateManager, blockpos3, rotation, structurePieceAccessor, random, structure, false, 0.8F);
                }
            }
        }
    }

    private static List<BlockPos> allPositions(RandomSource random, BlockPos pos) {
        List<BlockPos> list = Lists.newArrayList();
        list.add(pos.offset(-16 + Mth.nextInt(random, 1, 8), 0, 16 + Mth.nextInt(random, 1, 7)));
        list.add(pos.offset(-16 + Mth.nextInt(random, 1, 8), 0, Mth.nextInt(random, 1, 7)));
        list.add(pos.offset(-16 + Mth.nextInt(random, 1, 8), 0, -16 + Mth.nextInt(random, 4, 8)));
        list.add(pos.offset(Mth.nextInt(random, 1, 7), 0, 16 + Mth.nextInt(random, 1, 7)));
        list.add(pos.offset(Mth.nextInt(random, 1, 7), 0, -16 + Mth.nextInt(random, 4, 6)));
        list.add(pos.offset(16 + Mth.nextInt(random, 1, 7), 0, 16 + Mth.nextInt(random, 3, 8)));
        list.add(pos.offset(16 + Mth.nextInt(random, 1, 7), 0, Mth.nextInt(random, 1, 7)));
        list.add(pos.offset(16 + Mth.nextInt(random, 1, 7), 0, -16 + Mth.nextInt(random, 4, 8)));
        return list;
    }

    private static void addPiece(
        StructureTemplateManager structureTemplateManager,
        BlockPos pos,
        Rotation rotation,
        StructurePieceAccessor structurePieceAccessor,
        RandomSource random,
        OceanRuinStructure structure,
        boolean isLarge,
        float integrity
    ) {
        switch (structure.biomeTemp) {
            case WARM:
            default:
                ResourceLocation resourcelocation = isLarge ? getBigWarmRuin(random) : getSmallWarmRuin(random);
                structurePieceAccessor.addPiece(
                    new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, resourcelocation, pos, rotation, integrity, structure.biomeTemp, isLarge)
                );
                break;
            case COLD:
                ResourceLocation[] aresourcelocation = isLarge ? BIG_RUINS_BRICK : RUINS_BRICK;
                ResourceLocation[] aresourcelocation1 = isLarge ? BIG_RUINS_CRACKED : RUINS_CRACKED;
                ResourceLocation[] aresourcelocation2 = isLarge ? BIG_RUINS_MOSSY : RUINS_MOSSY;
                int i = random.nextInt(aresourcelocation.length);
                structurePieceAccessor.addPiece(
                    new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, aresourcelocation[i], pos, rotation, integrity, structure.biomeTemp, isLarge)
                );
                structurePieceAccessor.addPiece(
                    new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, aresourcelocation1[i], pos, rotation, 0.7F, structure.biomeTemp, isLarge)
                );
                structurePieceAccessor.addPiece(
                    new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, aresourcelocation2[i], pos, rotation, 0.5F, structure.biomeTemp, isLarge)
                );
        }
    }

    public static class OceanRuinPiece extends TemplateStructurePiece {
        private final OceanRuinStructure.Type biomeType;
        private final float integrity;
        private final boolean isLarge;

        public OceanRuinPiece(
            StructureTemplateManager structureTemplateManager,
            ResourceLocation location,
            BlockPos pos,
            Rotation rotation,
            float integrity,
            OceanRuinStructure.Type biomeType,
            boolean isLarge
        ) {
            super(StructurePieceType.OCEAN_RUIN, 0, structureTemplateManager, location, location.toString(), makeSettings(rotation, integrity, biomeType), pos);
            this.integrity = integrity;
            this.biomeType = biomeType;
            this.isLarge = isLarge;
        }

        private OceanRuinPiece(
            StructureTemplateManager structureTemplateManager,
            CompoundTag genDepth,
            Rotation rotation,
            float integrity,
            OceanRuinStructure.Type biomeType,
            boolean isLarge
        ) {
            super(StructurePieceType.OCEAN_RUIN, genDepth, structureTemplateManager, p_277332_ -> makeSettings(rotation, integrity, biomeType));
            this.integrity = integrity;
            this.biomeType = biomeType;
            this.isLarge = isLarge;
        }

        private static StructurePlaceSettings makeSettings(Rotation rotation, float integrity, OceanRuinStructure.Type structureType) {
            StructureProcessor structureprocessor = structureType == OceanRuinStructure.Type.COLD
                ? OceanRuinPieces.COLD_SUSPICIOUS_BLOCK_PROCESSOR
                : OceanRuinPieces.WARM_SUSPICIOUS_BLOCK_PROCESSOR;
            return new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .addProcessor(new BlockRotProcessor(integrity))
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR)
                .addProcessor(structureprocessor);
        }

        public static OceanRuinPieces.OceanRuinPiece create(StructureTemplateManager structureTemplateManager, CompoundTag tag) {
            Rotation rotation = Rotation.valueOf(tag.getString("Rot"));
            float f = tag.getFloat("Integrity");
            OceanRuinStructure.Type oceanruinstructure$type = OceanRuinStructure.Type.valueOf(tag.getString("BiomeType"));
            boolean flag = tag.getBoolean("IsLarge");
            return new OceanRuinPieces.OceanRuinPiece(structureTemplateManager, tag, rotation, f, oceanruinstructure$type, flag);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putString("Rot", this.placeSettings.getRotation().name());
            tag.putFloat("Integrity", this.integrity);
            tag.putString("BiomeType", this.biomeType.toString());
            tag.putBoolean("IsLarge", this.isLarge);
        }

        @Override
        protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
            if ("chest".equals(name)) {
                level.setBlock(
                    pos,
                    Blocks.CHEST.defaultBlockState().setValue(ChestBlock.WATERLOGGED, Boolean.valueOf(level.getFluidState(pos).is(FluidTags.WATER))),
                    2
                );
                BlockEntity blockentity = level.getBlockEntity(pos);
                if (blockentity instanceof ChestBlockEntity) {
                    ((ChestBlockEntity)blockentity)
                        .setLootTable(this.isLarge ? BuiltInLootTables.UNDERWATER_RUIN_BIG : BuiltInLootTables.UNDERWATER_RUIN_SMALL, random.nextLong());
                }
            } else if ("drowned".equals(name)) {
                Drowned drowned = EntityType.DROWNED.create(level.getLevel());
                if (drowned != null) {
                    drowned.setPersistenceRequired();
                    drowned.moveTo(pos, 0.0F, 0.0F);
                    drowned.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
                    level.addFreshEntityWithPassengers(drowned);
                    if (pos.getY() > level.getSeaLevel()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    } else {
                        level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                    }
                }
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
            int i = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, this.templatePosition.getX(), this.templatePosition.getZ());
            this.templatePosition = new BlockPos(this.templatePosition.getX(), i, this.templatePosition.getZ());
            BlockPos blockpos = StructureTemplate.transform(
                    new BlockPos(this.template.getSize().getX() - 1, 0, this.template.getSize().getZ() - 1),
                    Mirror.NONE,
                    this.placeSettings.getRotation(),
                    BlockPos.ZERO
                )
                .offset(this.templatePosition);
            this.templatePosition = new BlockPos(
                this.templatePosition.getX(), this.getHeight(this.templatePosition, level, blockpos), this.templatePosition.getZ()
            );
            super.postProcess(level, structureManager, generator, random, box, chunkPos, pos);
        }

        private int getHeight(BlockPos templatePos, BlockGetter level, BlockPos pos) {
            int i = templatePos.getY();
            int j = 512;
            int k = i - 1;
            int l = 0;

            for (BlockPos blockpos : BlockPos.betweenClosed(templatePos, pos)) {
                int i1 = blockpos.getX();
                int j1 = blockpos.getZ();
                int k1 = templatePos.getY() - 1;
                BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos(i1, k1, j1);
                BlockState blockstate = level.getBlockState(blockpos$mutableblockpos);

                for (FluidState fluidstate = level.getFluidState(blockpos$mutableblockpos);
                    (blockstate.isAir() || fluidstate.is(FluidTags.WATER) || blockstate.is(BlockTags.ICE)) && k1 > level.getMinBuildHeight() + 1;
                    fluidstate = level.getFluidState(blockpos$mutableblockpos)
                ) {
                    blockpos$mutableblockpos.set(i1, --k1, j1);
                    blockstate = level.getBlockState(blockpos$mutableblockpos);
                }

                j = Math.min(j, k1);
                if (k1 < k - 2) {
                    l++;
                }
            }

            int l1 = Math.abs(templatePos.getX() - pos.getX());
            if (k - j > 2 && l > l1 - 2) {
                i = j + 1;
            }

            return i;
        }
    }
}
