package net.minecraft.world.level.levelgen.structure;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

public class PoolElementStructurePiece extends StructurePiece {
    private static final Logger LOGGER = LogUtils.getLogger();
    protected final StructurePoolElement element;
    protected BlockPos position;
    private final int groundLevelDelta;
    protected final Rotation rotation;
    private final List<JigsawJunction> junctions = Lists.newArrayList();
    private final StructureTemplateManager structureTemplateManager;
    private final LiquidSettings liquidSettings;

    public PoolElementStructurePiece(
        StructureTemplateManager structureTemplateManager,
        StructurePoolElement element,
        BlockPos position,
        int groundLevelDelta,
        Rotation rotation,
        BoundingBox boundingBox,
        LiquidSettings liquidSettings
    ) {
        super(StructurePieceType.JIGSAW, 0, boundingBox);
        this.structureTemplateManager = structureTemplateManager;
        this.element = element;
        this.position = position;
        this.groundLevelDelta = groundLevelDelta;
        this.rotation = rotation;
        this.liquidSettings = liquidSettings;
    }

    public PoolElementStructurePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(StructurePieceType.JIGSAW, tag);
        this.structureTemplateManager = context.structureTemplateManager();
        this.position = new BlockPos(tag.getInt("PosX"), tag.getInt("PosY"), tag.getInt("PosZ"));
        this.groundLevelDelta = tag.getInt("ground_level_delta");
        DynamicOps<Tag> dynamicops = context.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        this.element = StructurePoolElement.CODEC
            .parse(dynamicops, tag.getCompound("pool_element"))
            .getPartialOrThrow(p_347416_ -> new IllegalStateException("Invalid pool element found: " + p_347416_));
        this.rotation = Rotation.valueOf(tag.getString("rotation"));
        this.boundingBox = this.element.getBoundingBox(this.structureTemplateManager, this.position, this.rotation);
        ListTag listtag = tag.getList("junctions", 10);
        this.junctions.clear();
        listtag.forEach(p_204943_ -> this.junctions.add(JigsawJunction.deserialize(new Dynamic<>(dynamicops, p_204943_))));
        this.liquidSettings = LiquidSettings.CODEC
            .parse(NbtOps.INSTANCE, tag.get("liquid_settings"))
            .result()
            .orElse(JigsawStructure.DEFAULT_LIQUID_SETTINGS);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("PosX", this.position.getX());
        tag.putInt("PosY", this.position.getY());
        tag.putInt("PosZ", this.position.getZ());
        tag.putInt("ground_level_delta", this.groundLevelDelta);
        DynamicOps<Tag> dynamicops = context.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        StructurePoolElement.CODEC
            .encodeStart(dynamicops, this.element)
            .resultOrPartial(LOGGER::error)
            .ifPresent(p_163125_ -> tag.put("pool_element", p_163125_));
        tag.putString("rotation", this.rotation.name());
        ListTag listtag = new ListTag();

        for (JigsawJunction jigsawjunction : this.junctions) {
            listtag.add(jigsawjunction.serialize(dynamicops).getValue());
        }

        tag.put("junctions", listtag);
        if (this.liquidSettings != JigsawStructure.DEFAULT_LIQUID_SETTINGS) {
            tag.put("liquid_settings", LiquidSettings.CODEC.encodeStart(NbtOps.INSTANCE, this.liquidSettings).getOrThrow());
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
        this.place(level, structureManager, generator, random, box, pos, false);
    }

    public void place(
        WorldGenLevel level,
        StructureManager structureManager,
        ChunkGenerator generator,
        RandomSource random,
        BoundingBox box,
        BlockPos pos,
        boolean keepJigsaws
    ) {
        this.element
            .place(
                this.structureTemplateManager,
                level,
                structureManager,
                generator,
                this.position,
                pos,
                this.rotation,
                box,
                random,
                this.liquidSettings,
                keepJigsaws
            );
    }

    @Override
    public void move(int x, int y, int z) {
        super.move(x, y, z);
        this.position = this.position.offset(x, y, z);
    }

    @Override
    public Rotation getRotation() {
        return this.rotation;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "<%s | %s | %s | %s>", this.getClass().getSimpleName(), this.position, this.rotation, this.element);
    }

    public StructurePoolElement getElement() {
        return this.element;
    }

    public BlockPos getPosition() {
        return this.position;
    }

    public int getGroundLevelDelta() {
        return this.groundLevelDelta;
    }

    public void addJunction(JigsawJunction junction) {
        this.junctions.add(junction);
    }

    public List<JigsawJunction> getJunctions() {
        return this.junctions;
    }
}
