package net.minecraft.world.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.StructureAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

public class StructureManager {
    private final LevelAccessor level;
    private final WorldOptions worldOptions;
    private final StructureCheck structureCheck;

    public StructureManager(LevelAccessor level, WorldOptions worldOptions, StructureCheck structureCheck) {
        this.level = level;
        this.worldOptions = worldOptions;
        this.structureCheck = structureCheck;
    }

    public StructureManager forWorldGenRegion(WorldGenRegion region) {
        if (region.getLevel() != this.level) {
            throw new IllegalStateException("Using invalid structure manager (source level: " + region.getLevel() + ", region: " + region);
        } else {
            return new StructureManager(region, this.worldOptions, this.structureCheck);
        }
    }

    public List<StructureStart> startsForStructure(ChunkPos chunkPos, Predicate<Structure> structurePredicate) {
        Map<Structure, LongSet> map = this.level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_REFERENCES).getAllReferences();
        Builder<StructureStart> builder = ImmutableList.builder();

        for (Entry<Structure, LongSet> entry : map.entrySet()) {
            Structure structure = entry.getKey();
            if (structurePredicate.test(structure)) {
                this.fillStartsForStructure(structure, entry.getValue(), builder::add);
            }
        }

        return builder.build();
    }

    public List<StructureStart> startsForStructure(SectionPos sectionPos, Structure structure) {
        LongSet longset = this.level.getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES).getReferencesForStructure(structure);
        Builder<StructureStart> builder = ImmutableList.builder();
        this.fillStartsForStructure(structure, longset, builder::add);
        return builder.build();
    }

    public void fillStartsForStructure(Structure structure, LongSet structureRefs, Consumer<StructureStart> startConsumer) {
        for (long i : structureRefs) {
            SectionPos sectionpos = SectionPos.of(new ChunkPos(i), this.level.getMinSection());
            StructureStart structurestart = this.getStartForStructure(
                sectionpos, structure, this.level.getChunk(sectionpos.x(), sectionpos.z(), ChunkStatus.STRUCTURE_STARTS)
            );
            if (structurestart != null && structurestart.isValid()) {
                startConsumer.accept(structurestart);
            }
        }
    }

    @Nullable
    public StructureStart getStartForStructure(SectionPos sectionPos, Structure structure, StructureAccess structureAccess) {
        return structureAccess.getStartForStructure(structure);
    }

    public void setStartForStructure(SectionPos sectionPos, Structure structure, StructureStart structureStart, StructureAccess structureAccess) {
        structureAccess.setStartForStructure(structure, structureStart);
    }

    public void addReferenceForStructure(SectionPos sectionPos, Structure structure, long reference, StructureAccess structureAccess) {
        structureAccess.addReferenceForStructure(structure, reference);
    }

    public boolean shouldGenerateStructures() {
        return this.worldOptions.generateStructures();
    }

    public StructureStart getStructureAt(BlockPos pos, Structure structure) {
        for (StructureStart structurestart : this.startsForStructure(SectionPos.of(pos), structure)) {
            if (structurestart.getBoundingBox().isInside(pos)) {
                return structurestart;
            }
        }

        return StructureStart.INVALID_START;
    }

    public StructureStart getStructureWithPieceAt(BlockPos pos, TagKey<Structure> structureTag) {
        return this.getStructureWithPieceAt(pos, p_330125_ -> p_330125_.is(structureTag));
    }

    public StructureStart getStructureWithPieceAt(BlockPos pos, HolderSet<Structure> structures) {
        return this.getStructureWithPieceAt(pos, structures::contains);
    }

    public StructureStart getStructureWithPieceAt(BlockPos pos, Predicate<Holder<Structure>> predicate) {
        Registry<Structure> registry = this.registryAccess().registryOrThrow(Registries.STRUCTURE);

        for (StructureStart structurestart : this.startsForStructure(
            new ChunkPos(pos), p_330128_ -> registry.getHolder(registry.getId(p_330128_)).map(predicate::test).orElse(false)
        )) {
            if (this.structureHasPieceAt(pos, structurestart)) {
                return structurestart;
            }
        }

        return StructureStart.INVALID_START;
    }

    public StructureStart getStructureWithPieceAt(BlockPos pos, Structure structure) {
        for (StructureStart structurestart : this.startsForStructure(SectionPos.of(pos), structure)) {
            if (this.structureHasPieceAt(pos, structurestart)) {
                return structurestart;
            }
        }

        return StructureStart.INVALID_START;
    }

    public boolean structureHasPieceAt(BlockPos pos, StructureStart structureStart) {
        for (StructurePiece structurepiece : structureStart.getPieces()) {
            if (structurepiece.getBoundingBox().isInside(pos)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasAnyStructureAt(BlockPos pos) {
        SectionPos sectionpos = SectionPos.of(pos);
        return this.level.getChunk(sectionpos.x(), sectionpos.z(), ChunkStatus.STRUCTURE_REFERENCES).hasAnyStructureReferences();
    }

    public Map<Structure, LongSet> getAllStructuresAt(BlockPos pos) {
        SectionPos sectionpos = SectionPos.of(pos);
        return this.level.getChunk(sectionpos.x(), sectionpos.z(), ChunkStatus.STRUCTURE_REFERENCES).getAllReferences();
    }

    public StructureCheckResult checkStructurePresence(ChunkPos chunkPos, Structure structure, StructurePlacement placement, boolean skipKnownStructures) {
        return this.structureCheck.checkStart(chunkPos, structure, placement, skipKnownStructures);
    }

    public void addReference(StructureStart structureStart) {
        structureStart.addReference();
        this.structureCheck.incrementReference(structureStart.getChunkPos(), structureStart.getStructure());
    }

    public RegistryAccess registryAccess() {
        return this.level.registryAccess();
    }
}
