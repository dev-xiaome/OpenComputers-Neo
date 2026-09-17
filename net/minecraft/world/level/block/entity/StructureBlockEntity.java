package net.minecraft.world.level.block.entity;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ResourceLocationException;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class StructureBlockEntity extends BlockEntity {
    private static final int SCAN_CORNER_BLOCKS_RANGE = 5;
    public static final int MAX_OFFSET_PER_AXIS = 48;
    public static final int MAX_SIZE_PER_AXIS = 48;
    public static final String AUTHOR_TAG = "author";
    @Nullable
    private ResourceLocation structureName;
    private String author = "";
    private String metaData = "";
    private BlockPos structurePos = new BlockPos(0, 1, 0);
    private Vec3i structureSize = Vec3i.ZERO;
    private Mirror mirror = Mirror.NONE;
    private Rotation rotation = Rotation.NONE;
    private StructureMode mode;
    private boolean ignoreEntities = true;
    private boolean powered;
    private boolean showAir;
    private boolean showBoundingBox = true;
    private float integrity = 1.0F;
    private long seed;

    public StructureBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.STRUCTURE_BLOCK, pos, blockState);
        this.mode = blockState.getValue(StructureBlock.MODE);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("name", this.getStructureName());
        tag.putString("author", this.author);
        tag.putString("metadata", this.metaData);
        tag.putInt("posX", this.structurePos.getX());
        tag.putInt("posY", this.structurePos.getY());
        tag.putInt("posZ", this.structurePos.getZ());
        tag.putInt("sizeX", this.structureSize.getX());
        tag.putInt("sizeY", this.structureSize.getY());
        tag.putInt("sizeZ", this.structureSize.getZ());
        tag.putString("rotation", this.rotation.toString());
        tag.putString("mirror", this.mirror.toString());
        tag.putString("mode", this.mode.toString());
        tag.putBoolean("ignoreEntities", this.ignoreEntities);
        tag.putBoolean("powered", this.powered);
        tag.putBoolean("showair", this.showAir);
        tag.putBoolean("showboundingbox", this.showBoundingBox);
        tag.putFloat("integrity", this.integrity);
        tag.putLong("seed", this.seed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.setStructureName(tag.getString("name"));
        this.author = tag.getString("author");
        this.metaData = tag.getString("metadata");
        int i = Mth.clamp(tag.getInt("posX"), -48, 48);
        int j = Mth.clamp(tag.getInt("posY"), -48, 48);
        int k = Mth.clamp(tag.getInt("posZ"), -48, 48);
        this.structurePos = new BlockPos(i, j, k);
        int l = Mth.clamp(tag.getInt("sizeX"), 0, 48);
        int i1 = Mth.clamp(tag.getInt("sizeY"), 0, 48);
        int j1 = Mth.clamp(tag.getInt("sizeZ"), 0, 48);
        this.structureSize = new Vec3i(l, i1, j1);

        try {
            this.rotation = Rotation.valueOf(tag.getString("rotation"));
        } catch (IllegalArgumentException illegalargumentexception2) {
            this.rotation = Rotation.NONE;
        }

        try {
            this.mirror = Mirror.valueOf(tag.getString("mirror"));
        } catch (IllegalArgumentException illegalargumentexception1) {
            this.mirror = Mirror.NONE;
        }

        try {
            this.mode = StructureMode.valueOf(tag.getString("mode"));
        } catch (IllegalArgumentException illegalargumentexception) {
            this.mode = StructureMode.DATA;
        }

        this.ignoreEntities = tag.getBoolean("ignoreEntities");
        this.powered = tag.getBoolean("powered");
        this.showAir = tag.getBoolean("showair");
        this.showBoundingBox = tag.getBoolean("showboundingbox");
        if (tag.contains("integrity")) {
            this.integrity = tag.getFloat("integrity");
        } else {
            this.integrity = 1.0F;
        }

        this.seed = tag.getLong("seed");
        this.updateBlockState();
    }

    private void updateBlockState() {
        if (this.level != null) {
            BlockPos blockpos = this.getBlockPos();
            BlockState blockstate = this.level.getBlockState(blockpos);
            if (blockstate.is(Blocks.STRUCTURE_BLOCK)) {
                this.level.setBlock(blockpos, blockstate.setValue(StructureBlock.MODE, this.mode), 2);
            }
        }
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public boolean usedBy(Player player) {
        if (!player.canUseGameMasterBlocks()) {
            return false;
        } else {
            if (player.getCommandSenderWorld().isClientSide) {
                player.openStructureBlock(this);
            }

            return true;
        }
    }

    public String getStructureName() {
        return this.structureName == null ? "" : this.structureName.toString();
    }

    public boolean hasStructureName() {
        return this.structureName != null;
    }

    public void setStructureName(@Nullable String structureName) {
        this.setStructureName(StringUtil.isNullOrEmpty(structureName) ? null : ResourceLocation.tryParse(structureName));
    }

    public void setStructureName(@Nullable ResourceLocation structureName) {
        this.structureName = structureName;
    }

    public void createdBy(LivingEntity author) {
        this.author = author.getName().getString();
    }

    public BlockPos getStructurePos() {
        return this.structurePos;
    }

    public void setStructurePos(BlockPos structurePos) {
        this.structurePos = structurePos;
    }

    public Vec3i getStructureSize() {
        return this.structureSize;
    }

    public void setStructureSize(Vec3i structureSize) {
        this.structureSize = structureSize;
    }

    public Mirror getMirror() {
        return this.mirror;
    }

    public void setMirror(Mirror mirror) {
        this.mirror = mirror;
    }

    public Rotation getRotation() {
        return this.rotation;
    }

    public void setRotation(Rotation rotation) {
        this.rotation = rotation;
    }

    public String getMetaData() {
        return this.metaData;
    }

    public void setMetaData(String metaData) {
        this.metaData = metaData;
    }

    public StructureMode getMode() {
        return this.mode;
    }

    public void setMode(StructureMode mode) {
        this.mode = mode;
        BlockState blockstate = this.level.getBlockState(this.getBlockPos());
        if (blockstate.is(Blocks.STRUCTURE_BLOCK)) {
            this.level.setBlock(this.getBlockPos(), blockstate.setValue(StructureBlock.MODE, mode), 2);
        }
    }

    public boolean isIgnoreEntities() {
        return this.ignoreEntities;
    }

    public void setIgnoreEntities(boolean ignoreEntities) {
        this.ignoreEntities = ignoreEntities;
    }

    public float getIntegrity() {
        return this.integrity;
    }

    public void setIntegrity(float integrity) {
        this.integrity = integrity;
    }

    public long getSeed() {
        return this.seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public boolean detectSize() {
        if (this.mode != StructureMode.SAVE) {
            return false;
        } else {
            BlockPos blockpos = this.getBlockPos();
            int i = 80;
            BlockPos blockpos1 = new BlockPos(blockpos.getX() - 80, this.level.getMinBuildHeight(), blockpos.getZ() - 80);
            BlockPos blockpos2 = new BlockPos(blockpos.getX() + 80, this.level.getMaxBuildHeight() - 1, blockpos.getZ() + 80);
            Stream<BlockPos> stream = this.getRelatedCorners(blockpos1, blockpos2);
            return calculateEnclosingBoundingBox(blockpos, stream)
                .filter(
                    p_155790_ -> {
                        int j = p_155790_.maxX() - p_155790_.minX();
                        int k = p_155790_.maxY() - p_155790_.minY();
                        int l = p_155790_.maxZ() - p_155790_.minZ();
                        if (j > 1 && k > 1 && l > 1) {
                            this.structurePos = new BlockPos(
                                p_155790_.minX() - blockpos.getX() + 1, p_155790_.minY() - blockpos.getY() + 1, p_155790_.minZ() - blockpos.getZ() + 1
                            );
                            this.structureSize = new Vec3i(j - 1, k - 1, l - 1);
                            this.setChanged();
                            BlockState blockstate = this.level.getBlockState(blockpos);
                            this.level.sendBlockUpdated(blockpos, blockstate, blockstate, 3);
                            return true;
                        } else {
                            return false;
                        }
                    }
                )
                .isPresent();
        }
    }

    private Stream<BlockPos> getRelatedCorners(BlockPos minPos, BlockPos maxPos) {
        return BlockPos.betweenClosedStream(minPos, maxPos)
            .filter(p_272561_ -> this.level.getBlockState(p_272561_).is(Blocks.STRUCTURE_BLOCK))
            .map(this.level::getBlockEntity)
            .filter(p_155802_ -> p_155802_ instanceof StructureBlockEntity)
            .map(p_155785_ -> (StructureBlockEntity)p_155785_)
            .filter(p_155787_ -> p_155787_.mode == StructureMode.CORNER && Objects.equals(this.structureName, p_155787_.structureName))
            .map(BlockEntity::getBlockPos);
    }

    private static Optional<BoundingBox> calculateEnclosingBoundingBox(BlockPos pos, Stream<BlockPos> relatedCorners) {
        Iterator<BlockPos> iterator = relatedCorners.iterator();
        if (!iterator.hasNext()) {
            return Optional.empty();
        } else {
            BlockPos blockpos = iterator.next();
            BoundingBox boundingbox = new BoundingBox(blockpos);
            if (iterator.hasNext()) {
                iterator.forEachRemaining(boundingbox::encapsulate);
            } else {
                boundingbox.encapsulate(pos);
            }

            return Optional.of(boundingbox);
        }
    }

    public boolean saveStructure() {
        return this.mode != StructureMode.SAVE ? false : this.saveStructure(true);
    }

    /**
     * Saves the template, either updating the local version or writing it to disk.
     *
     * @return true if the template was successfully saved.
     */
    public boolean saveStructure(boolean writeToDisk) {
        if (this.structureName == null) {
            return false;
        } else {
            BlockPos blockpos = this.getBlockPos().offset(this.structurePos);
            ServerLevel serverlevel = (ServerLevel)this.level;
            StructureTemplateManager structuretemplatemanager = serverlevel.getStructureManager();

            StructureTemplate structuretemplate;
            try {
                structuretemplate = structuretemplatemanager.getOrCreate(this.structureName);
            } catch (ResourceLocationException resourcelocationexception1) {
                return false;
            }

            structuretemplate.fillFromWorld(this.level, blockpos, this.structureSize, !this.ignoreEntities, Blocks.STRUCTURE_VOID);
            structuretemplate.setAuthor(this.author);
            if (writeToDisk) {
                try {
                    return structuretemplatemanager.save(this.structureName);
                } catch (ResourceLocationException resourcelocationexception) {
                    return false;
                }
            } else {
                return true;
            }
        }
    }

    public static RandomSource createRandom(long seed) {
        return seed == 0L ? RandomSource.create(Util.getMillis()) : RandomSource.create(seed);
    }

    public boolean placeStructureIfSameSize(ServerLevel level) {
        if (this.mode == StructureMode.LOAD && this.structureName != null) {
            StructureTemplate structuretemplate = level.getStructureManager().get(this.structureName).orElse(null);
            if (structuretemplate == null) {
                return false;
            } else if (structuretemplate.getSize().equals(this.structureSize)) {
                this.placeStructure(level, structuretemplate);
                return true;
            } else {
                this.loadStructureInfo(structuretemplate);
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean loadStructureInfo(ServerLevel level) {
        StructureTemplate structuretemplate = this.getStructureTemplate(level);
        if (structuretemplate == null) {
            return false;
        } else {
            this.loadStructureInfo(structuretemplate);
            return true;
        }
    }

    private void loadStructureInfo(StructureTemplate structureTemplate) {
        this.author = !StringUtil.isNullOrEmpty(structureTemplate.getAuthor()) ? structureTemplate.getAuthor() : "";
        this.structureSize = structureTemplate.getSize();
        this.setChanged();
    }

    public void placeStructure(ServerLevel level) {
        StructureTemplate structuretemplate = this.getStructureTemplate(level);
        if (structuretemplate != null) {
            this.placeStructure(level, structuretemplate);
        }
    }

    @Nullable
    private StructureTemplate getStructureTemplate(ServerLevel level) {
        return this.structureName == null ? null : level.getStructureManager().get(this.structureName).orElse(null);
    }

    private void placeStructure(ServerLevel level, StructureTemplate structureTemplate) {
        this.loadStructureInfo(structureTemplate);
        StructurePlaceSettings structureplacesettings = new StructurePlaceSettings()
            .setMirror(this.mirror)
            .setRotation(this.rotation)
            .setIgnoreEntities(this.ignoreEntities);
        if (this.integrity < 1.0F) {
            structureplacesettings.clearProcessors()
                .addProcessor(new BlockRotProcessor(Mth.clamp(this.integrity, 0.0F, 1.0F)))
                .setRandom(createRandom(this.seed));
        }

        BlockPos blockpos = this.getBlockPos().offset(this.structurePos);
        structureTemplate.placeInWorld(level, blockpos, blockpos, structureplacesettings, createRandom(this.seed), 2);
    }

    public void unloadStructure() {
        if (this.structureName != null) {
            ServerLevel serverlevel = (ServerLevel)this.level;
            StructureTemplateManager structuretemplatemanager = serverlevel.getStructureManager();
            structuretemplatemanager.remove(this.structureName);
        }
    }

    public boolean isStructureLoadable() {
        if (this.mode == StructureMode.LOAD && !this.level.isClientSide && this.structureName != null) {
            ServerLevel serverlevel = (ServerLevel)this.level;
            StructureTemplateManager structuretemplatemanager = serverlevel.getStructureManager();

            try {
                return structuretemplatemanager.get(this.structureName).isPresent();
            } catch (ResourceLocationException resourcelocationexception) {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean isPowered() {
        return this.powered;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    public boolean getShowAir() {
        return this.showAir;
    }

    public void setShowAir(boolean showAir) {
        this.showAir = showAir;
    }

    public boolean getShowBoundingBox() {
        return this.showBoundingBox;
    }

    public void setShowBoundingBox(boolean showBoundingBox) {
        this.showBoundingBox = showBoundingBox;
    }

    public static enum UpdateType {
        UPDATE_DATA,
        SAVE_AREA,
        LOAD_AREA,
        SCAN_AREA;
    }
}
