package net.minecraft.data.structures;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

public class StructureUpdater implements SnbtToNbt.Filter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = PackType.SERVER_DATA.getDirectory() + "/minecraft/structure/";

    @Override
    public CompoundTag apply(String structureLocationPath, CompoundTag tag) {
        return structureLocationPath.startsWith(PREFIX) ? update(structureLocationPath, tag) : tag;
    }

    public static CompoundTag update(String structureLocationPath, CompoundTag tag) {
        StructureTemplate structuretemplate = new StructureTemplate();
        int i = NbtUtils.getDataVersion(tag, 500);
        int j = 3937;
        if (i < 3937) {
            LOGGER.warn("SNBT Too old, do not forget to update: {} < {}: {}", i, 3937, structureLocationPath);
        }

        CompoundTag compoundtag = DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), tag, i);
        structuretemplate.load(BuiltInRegistries.BLOCK.asLookup(), compoundtag);
        return structuretemplate.save(new CompoundTag());
    }
}
