package net.minecraft.world.level.levelgen.structure.pieces;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.slf4j.Logger;

public record PiecesContainer(List<StructurePiece> pieces) {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation JIGSAW_RENAME = ResourceLocation.withDefaultNamespace("jigsaw");
    private static final Map<ResourceLocation, ResourceLocation> RENAMES = ImmutableMap.<ResourceLocation, ResourceLocation>builder()
        .put(ResourceLocation.withDefaultNamespace("nvi"), JIGSAW_RENAME)
        .put(ResourceLocation.withDefaultNamespace("pcp"), JIGSAW_RENAME)
        .put(ResourceLocation.withDefaultNamespace("bastionremnant"), JIGSAW_RENAME)
        .put(ResourceLocation.withDefaultNamespace("runtime"), JIGSAW_RENAME)
        .build();

    public PiecesContainer(List<StructurePiece> pieces) {
        this.pieces = List.copyOf(pieces);
    }

    public boolean isEmpty() {
        return this.pieces.isEmpty();
    }

    public boolean isInsidePiece(BlockPos pos) {
        for (StructurePiece structurepiece : this.pieces) {
            if (structurepiece.getBoundingBox().isInside(pos)) {
                return true;
            }
        }

        return false;
    }

    public Tag save(StructurePieceSerializationContext context) {
        ListTag listtag = new ListTag();

        for (StructurePiece structurepiece : this.pieces) {
            listtag.add(structurepiece.createTag(context));
        }

        return listtag;
    }

    public static PiecesContainer load(ListTag tag, StructurePieceSerializationContext context) {
        List<StructurePiece> list = Lists.newArrayList();

        for (int i = 0; i < tag.size(); i++) {
            CompoundTag compoundtag = tag.getCompound(i);
            String s = compoundtag.getString("id").toLowerCase(Locale.ROOT);
            ResourceLocation resourcelocation = ResourceLocation.parse(s);
            ResourceLocation resourcelocation1 = RENAMES.getOrDefault(resourcelocation, resourcelocation);
            StructurePieceType structurepiecetype = BuiltInRegistries.STRUCTURE_PIECE.get(resourcelocation1);
            if (structurepiecetype == null) {
                LOGGER.error("Unknown structure piece id: {}", resourcelocation1);
            } else {
                try {
                    StructurePiece structurepiece = structurepiecetype.load(context, compoundtag);
                    list.add(structurepiece);
                } catch (Exception exception) {
                    LOGGER.error("Exception loading structure piece with id {}", resourcelocation1, exception);
                }
            }
        }

        return new PiecesContainer(list);
    }

    public BoundingBox calculateBoundingBox() {
        return StructurePiece.createBoundingBox(this.pieces.stream());
    }
}
