package net.minecraft.data.models.model;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class TextureMapping {
    private final Map<TextureSlot, ResourceLocation> slots = Maps.newHashMap();
    private final Set<TextureSlot> forcedSlots = Sets.newHashSet();

    public TextureMapping put(TextureSlot textureSlot, ResourceLocation textureLocation) {
        this.slots.put(textureSlot, textureLocation);
        return this;
    }

    public TextureMapping putForced(TextureSlot textureSlot, ResourceLocation textureLocation) {
        this.slots.put(textureSlot, textureLocation);
        this.forcedSlots.add(textureSlot);
        return this;
    }

    public Stream<TextureSlot> getForced() {
        return this.forcedSlots.stream();
    }

    public TextureMapping copySlot(TextureSlot sourceSlot, TextureSlot targetSlot) {
        this.slots.put(targetSlot, this.slots.get(sourceSlot));
        return this;
    }

    public TextureMapping copyForced(TextureSlot sourceSlot, TextureSlot targetSlot) {
        this.slots.put(targetSlot, this.slots.get(sourceSlot));
        this.forcedSlots.add(targetSlot);
        return this;
    }

    public ResourceLocation get(TextureSlot textureSlot) {
        for (TextureSlot textureslot = textureSlot; textureslot != null; textureslot = textureslot.getParent()) {
            ResourceLocation resourcelocation = this.slots.get(textureslot);
            if (resourcelocation != null) {
                return resourcelocation;
            }
        }

        throw new IllegalStateException("Can't find texture for slot " + textureSlot);
    }

    public TextureMapping copyAndUpdate(TextureSlot textureSlot, ResourceLocation textureLocation) {
        TextureMapping texturemapping = new TextureMapping();
        texturemapping.slots.putAll(this.slots);
        texturemapping.forcedSlots.addAll(this.forcedSlots);
        texturemapping.put(textureSlot, textureLocation);
        return texturemapping;
    }

    public static TextureMapping cube(Block block) {
        ResourceLocation resourcelocation = getBlockTexture(block);
        return cube(resourcelocation);
    }

    public static TextureMapping defaultTexture(Block block) {
        ResourceLocation resourcelocation = getBlockTexture(block);
        return defaultTexture(resourcelocation);
    }

    public static TextureMapping defaultTexture(ResourceLocation textureLocation) {
        return new TextureMapping().put(TextureSlot.TEXTURE, textureLocation);
    }

    public static TextureMapping cube(ResourceLocation allTextureLocation) {
        return new TextureMapping().put(TextureSlot.ALL, allTextureLocation);
    }

    public static TextureMapping cross(Block block) {
        return singleSlot(TextureSlot.CROSS, getBlockTexture(block));
    }

    public static TextureMapping cross(ResourceLocation crossTextureLocation) {
        return singleSlot(TextureSlot.CROSS, crossTextureLocation);
    }

    public static TextureMapping plant(Block plantBlock) {
        return singleSlot(TextureSlot.PLANT, getBlockTexture(plantBlock));
    }

    public static TextureMapping plant(ResourceLocation plantTextureLocation) {
        return singleSlot(TextureSlot.PLANT, plantTextureLocation);
    }

    public static TextureMapping rail(Block railBlock) {
        return singleSlot(TextureSlot.RAIL, getBlockTexture(railBlock));
    }

    public static TextureMapping rail(ResourceLocation railTextureLocation) {
        return singleSlot(TextureSlot.RAIL, railTextureLocation);
    }

    public static TextureMapping wool(Block woolBlock) {
        return singleSlot(TextureSlot.WOOL, getBlockTexture(woolBlock));
    }

    public static TextureMapping flowerbed(Block flowerbedBlock) {
        return new TextureMapping().put(TextureSlot.FLOWERBED, getBlockTexture(flowerbedBlock)).put(TextureSlot.STEM, getBlockTexture(flowerbedBlock, "_stem"));
    }

    public static TextureMapping wool(ResourceLocation woolTextureLocation) {
        return singleSlot(TextureSlot.WOOL, woolTextureLocation);
    }

    public static TextureMapping stem(Block stemBlock) {
        return singleSlot(TextureSlot.STEM, getBlockTexture(stemBlock));
    }

    public static TextureMapping attachedStem(Block unattachedStemBlock, Block attachedStemBlock) {
        return new TextureMapping().put(TextureSlot.STEM, getBlockTexture(unattachedStemBlock)).put(TextureSlot.UPPER_STEM, getBlockTexture(attachedStemBlock));
    }

    public static TextureMapping pattern(Block patternBlock) {
        return singleSlot(TextureSlot.PATTERN, getBlockTexture(patternBlock));
    }

    public static TextureMapping fan(Block fanBlock) {
        return singleSlot(TextureSlot.FAN, getBlockTexture(fanBlock));
    }

    public static TextureMapping crop(ResourceLocation cropTextureLocation) {
        return singleSlot(TextureSlot.CROP, cropTextureLocation);
    }

    public static TextureMapping pane(Block glassBlock, Block paneBlock) {
        return new TextureMapping().put(TextureSlot.PANE, getBlockTexture(glassBlock)).put(TextureSlot.EDGE, getBlockTexture(paneBlock, "_top"));
    }

    public static TextureMapping singleSlot(TextureSlot textureSlot, ResourceLocation textureLocation) {
        return new TextureMapping().put(textureSlot, textureLocation);
    }

    public static TextureMapping column(Block columnBlock) {
        return new TextureMapping().put(TextureSlot.SIDE, getBlockTexture(columnBlock, "_side")).put(TextureSlot.END, getBlockTexture(columnBlock, "_top"));
    }

    public static TextureMapping cubeTop(Block block) {
        return new TextureMapping().put(TextureSlot.SIDE, getBlockTexture(block, "_side")).put(TextureSlot.TOP, getBlockTexture(block, "_top"));
    }

    public static TextureMapping pottedAzalea(Block azaleaBlock) {
        return new TextureMapping()
            .put(TextureSlot.PLANT, getBlockTexture(azaleaBlock, "_plant"))
            .put(TextureSlot.SIDE, getBlockTexture(azaleaBlock, "_side"))
            .put(TextureSlot.TOP, getBlockTexture(azaleaBlock, "_top"));
    }

    public static TextureMapping logColumn(Block logBlock) {
        return new TextureMapping()
            .put(TextureSlot.SIDE, getBlockTexture(logBlock))
            .put(TextureSlot.END, getBlockTexture(logBlock, "_top"))
            .put(TextureSlot.PARTICLE, getBlockTexture(logBlock));
    }

    public static TextureMapping column(ResourceLocation sideTextureLocation, ResourceLocation endTextureLocation) {
        return new TextureMapping().put(TextureSlot.SIDE, sideTextureLocation).put(TextureSlot.END, endTextureLocation);
    }

    public static TextureMapping fence(Block fenceBlock) {
        return new TextureMapping()
            .put(TextureSlot.TEXTURE, getBlockTexture(fenceBlock))
            .put(TextureSlot.SIDE, getBlockTexture(fenceBlock, "_side"))
            .put(TextureSlot.TOP, getBlockTexture(fenceBlock, "_top"));
    }

    public static TextureMapping customParticle(Block block) {
        return new TextureMapping().put(TextureSlot.TEXTURE, getBlockTexture(block)).put(TextureSlot.PARTICLE, getBlockTexture(block, "_particle"));
    }

    public static TextureMapping cubeBottomTop(Block block) {
        return new TextureMapping()
            .put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
            .put(TextureSlot.TOP, getBlockTexture(block, "_top"))
            .put(TextureSlot.BOTTOM, getBlockTexture(block, "_bottom"));
    }

    public static TextureMapping cubeBottomTopWithWall(Block block) {
        ResourceLocation resourcelocation = getBlockTexture(block);
        return new TextureMapping()
            .put(TextureSlot.WALL, resourcelocation)
            .put(TextureSlot.SIDE, resourcelocation)
            .put(TextureSlot.TOP, getBlockTexture(block, "_top"))
            .put(TextureSlot.BOTTOM, getBlockTexture(block, "_bottom"));
    }

    public static TextureMapping columnWithWall(Block columnBlock) {
        ResourceLocation resourcelocation = getBlockTexture(columnBlock);
        return new TextureMapping()
            .put(TextureSlot.TEXTURE, resourcelocation)
            .put(TextureSlot.WALL, resourcelocation)
            .put(TextureSlot.SIDE, resourcelocation)
            .put(TextureSlot.END, getBlockTexture(columnBlock, "_top"));
    }

    public static TextureMapping door(ResourceLocation topTextureLocation, ResourceLocation bottomTextureLocation) {
        return new TextureMapping().put(TextureSlot.TOP, topTextureLocation).put(TextureSlot.BOTTOM, bottomTextureLocation);
    }

    public static TextureMapping door(Block doorBlock) {
        return new TextureMapping().put(TextureSlot.TOP, getBlockTexture(doorBlock, "_top")).put(TextureSlot.BOTTOM, getBlockTexture(doorBlock, "_bottom"));
    }

    public static TextureMapping particle(Block particleBlock) {
        return new TextureMapping().put(TextureSlot.PARTICLE, getBlockTexture(particleBlock));
    }

    public static TextureMapping particle(ResourceLocation textureLocation) {
        return new TextureMapping().put(TextureSlot.PARTICLE, textureLocation);
    }

    public static TextureMapping fire0(Block fireBlock) {
        return new TextureMapping().put(TextureSlot.FIRE, getBlockTexture(fireBlock, "_0"));
    }

    public static TextureMapping fire1(Block fireBlock) {
        return new TextureMapping().put(TextureSlot.FIRE, getBlockTexture(fireBlock, "_1"));
    }

    public static TextureMapping lantern(Block lanternBlock) {
        return new TextureMapping().put(TextureSlot.LANTERN, getBlockTexture(lanternBlock));
    }

    public static TextureMapping torch(Block torchBlock) {
        return new TextureMapping().put(TextureSlot.TORCH, getBlockTexture(torchBlock));
    }

    public static TextureMapping torch(ResourceLocation torchTextureLocation) {
        return new TextureMapping().put(TextureSlot.TORCH, torchTextureLocation);
    }

    public static TextureMapping trialSpawner(Block trialSpawnerBlock, String sideSuffix, String topSuffix) {
        return new TextureMapping()
            .put(TextureSlot.SIDE, getBlockTexture(trialSpawnerBlock, sideSuffix))
            .put(TextureSlot.TOP, getBlockTexture(trialSpawnerBlock, topSuffix))
            .put(TextureSlot.BOTTOM, getBlockTexture(trialSpawnerBlock, "_bottom"));
    }

    public static TextureMapping vault(Block vaultBlock, String frontSuffix, String sideSuffix, String topSuffix, String bottomSuffix) {
        return new TextureMapping()
            .put(TextureSlot.FRONT, getBlockTexture(vaultBlock, frontSuffix))
            .put(TextureSlot.SIDE, getBlockTexture(vaultBlock, sideSuffix))
            .put(TextureSlot.TOP, getBlockTexture(vaultBlock, topSuffix))
            .put(TextureSlot.BOTTOM, getBlockTexture(vaultBlock, bottomSuffix));
    }

    public static TextureMapping particleFromItem(Item particleItem) {
        return new TextureMapping().put(TextureSlot.PARTICLE, getItemTexture(particleItem));
    }

    public static TextureMapping commandBlock(Block commandBlock) {
        return new TextureMapping()
            .put(TextureSlot.SIDE, getBlockTexture(commandBlock, "_side"))
            .put(TextureSlot.FRONT, getBlockTexture(commandBlock, "_front"))
            .put(TextureSlot.BACK, getBlockTexture(commandBlock, "_back"));
    }

    public static TextureMapping orientableCube(Block block) {
        return new TextureMapping()
            .put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
            .put(TextureSlot.FRONT, getBlockTexture(block, "_front"))
            .put(TextureSlot.TOP, getBlockTexture(block, "_top"))
            .put(TextureSlot.BOTTOM, getBlockTexture(block, "_bottom"));
    }

    public static TextureMapping orientableCubeOnlyTop(Block block) {
        return new TextureMapping()
            .put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
            .put(TextureSlot.FRONT, getBlockTexture(block, "_front"))
            .put(TextureSlot.TOP, getBlockTexture(block, "_top"));
    }

    public static TextureMapping orientableCubeSameEnds(Block block) {
        return new TextureMapping()
            .put(TextureSlot.SIDE, getBlockTexture(block, "_side"))
            .put(TextureSlot.FRONT, getBlockTexture(block, "_front"))
            .put(TextureSlot.END, getBlockTexture(block, "_end"));
    }

    public static TextureMapping top(Block block) {
        return new TextureMapping().put(TextureSlot.TOP, getBlockTexture(block, "_top"));
    }

    public static TextureMapping craftingTable(Block craftingTableBlock, Block craftingTableMaterialBlock) {
        return new TextureMapping()
            .put(TextureSlot.PARTICLE, getBlockTexture(craftingTableBlock, "_front"))
            .put(TextureSlot.DOWN, getBlockTexture(craftingTableMaterialBlock))
            .put(TextureSlot.UP, getBlockTexture(craftingTableBlock, "_top"))
            .put(TextureSlot.NORTH, getBlockTexture(craftingTableBlock, "_front"))
            .put(TextureSlot.EAST, getBlockTexture(craftingTableBlock, "_side"))
            .put(TextureSlot.SOUTH, getBlockTexture(craftingTableBlock, "_side"))
            .put(TextureSlot.WEST, getBlockTexture(craftingTableBlock, "_front"));
    }

    public static TextureMapping fletchingTable(Block fletchingTableBlock, Block fletchingTableMaterialBlock) {
        return new TextureMapping()
            .put(TextureSlot.PARTICLE, getBlockTexture(fletchingTableBlock, "_front"))
            .put(TextureSlot.DOWN, getBlockTexture(fletchingTableMaterialBlock))
            .put(TextureSlot.UP, getBlockTexture(fletchingTableBlock, "_top"))
            .put(TextureSlot.NORTH, getBlockTexture(fletchingTableBlock, "_front"))
            .put(TextureSlot.SOUTH, getBlockTexture(fletchingTableBlock, "_front"))
            .put(TextureSlot.EAST, getBlockTexture(fletchingTableBlock, "_side"))
            .put(TextureSlot.WEST, getBlockTexture(fletchingTableBlock, "_side"));
    }

    public static TextureMapping snifferEgg(String crackLevel) {
        return new TextureMapping()
            .put(TextureSlot.PARTICLE, getBlockTexture(Blocks.SNIFFER_EGG, crackLevel + "_north"))
            .put(TextureSlot.BOTTOM, getBlockTexture(Blocks.SNIFFER_EGG, crackLevel + "_bottom"))
            .put(TextureSlot.TOP, getBlockTexture(Blocks.SNIFFER_EGG, crackLevel + "_top"))
            .put(TextureSlot.NORTH, getBlockTexture(Blocks.SNIFFER_EGG, crackLevel + "_north"))
            .put(TextureSlot.SOUTH, getBlockTexture(Blocks.SNIFFER_EGG, crackLevel + "_south"))
            .put(TextureSlot.EAST, getBlockTexture(Blocks.SNIFFER_EGG, crackLevel + "_east"))
            .put(TextureSlot.WEST, getBlockTexture(Blocks.SNIFFER_EGG, crackLevel + "_west"));
    }

    public static TextureMapping campfire(Block campfireBlock) {
        return new TextureMapping().put(TextureSlot.LIT_LOG, getBlockTexture(campfireBlock, "_log_lit")).put(TextureSlot.FIRE, getBlockTexture(campfireBlock, "_fire"));
    }

    public static TextureMapping candleCake(Block candleCakeBlock, boolean lit) {
        return new TextureMapping()
            .put(TextureSlot.PARTICLE, getBlockTexture(Blocks.CAKE, "_side"))
            .put(TextureSlot.BOTTOM, getBlockTexture(Blocks.CAKE, "_bottom"))
            .put(TextureSlot.TOP, getBlockTexture(Blocks.CAKE, "_top"))
            .put(TextureSlot.SIDE, getBlockTexture(Blocks.CAKE, "_side"))
            .put(TextureSlot.CANDLE, getBlockTexture(candleCakeBlock, lit ? "_lit" : ""));
    }

    public static TextureMapping cauldron(ResourceLocation cauldronContentTextureLocation) {
        return new TextureMapping()
            .put(TextureSlot.PARTICLE, getBlockTexture(Blocks.CAULDRON, "_side"))
            .put(TextureSlot.SIDE, getBlockTexture(Blocks.CAULDRON, "_side"))
            .put(TextureSlot.TOP, getBlockTexture(Blocks.CAULDRON, "_top"))
            .put(TextureSlot.BOTTOM, getBlockTexture(Blocks.CAULDRON, "_bottom"))
            .put(TextureSlot.INSIDE, getBlockTexture(Blocks.CAULDRON, "_inner"))
            .put(TextureSlot.CONTENT, cauldronContentTextureLocation);
    }

    public static TextureMapping sculkShrieker(boolean canSummon) {
        String s = canSummon ? "_can_summon" : "";
        return new TextureMapping()
            .put(TextureSlot.PARTICLE, getBlockTexture(Blocks.SCULK_SHRIEKER, "_bottom"))
            .put(TextureSlot.SIDE, getBlockTexture(Blocks.SCULK_SHRIEKER, "_side"))
            .put(TextureSlot.TOP, getBlockTexture(Blocks.SCULK_SHRIEKER, "_top"))
            .put(TextureSlot.INNER_TOP, getBlockTexture(Blocks.SCULK_SHRIEKER, s + "_inner_top"))
            .put(TextureSlot.BOTTOM, getBlockTexture(Blocks.SCULK_SHRIEKER, "_bottom"));
    }

    public static TextureMapping layer0(Item layerZeroItem) {
        return new TextureMapping().put(TextureSlot.LAYER0, getItemTexture(layerZeroItem));
    }

    public static TextureMapping layer0(Block layerZeroBlock) {
        return new TextureMapping().put(TextureSlot.LAYER0, getBlockTexture(layerZeroBlock));
    }

    public static TextureMapping layer0(ResourceLocation layerZeroTextureLocation) {
        return new TextureMapping().put(TextureSlot.LAYER0, layerZeroTextureLocation);
    }

    public static TextureMapping layered(ResourceLocation layer0, ResourceLocation layer1) {
        return new TextureMapping().put(TextureSlot.LAYER0, layer0).put(TextureSlot.LAYER1, layer1);
    }

    public static TextureMapping layered(ResourceLocation layer0, ResourceLocation layer1, ResourceLocation layer2) {
        return new TextureMapping().put(TextureSlot.LAYER0, layer0).put(TextureSlot.LAYER1, layer1).put(TextureSlot.LAYER2, layer2);
    }

    public static ResourceLocation getBlockTexture(Block block) {
        ResourceLocation resourcelocation = BuiltInRegistries.BLOCK.getKey(block);
        return resourcelocation.withPrefix("block/");
    }

    public static ResourceLocation getBlockTexture(Block block, String textureSuffix) {
        ResourceLocation resourcelocation = BuiltInRegistries.BLOCK.getKey(block);
        return resourcelocation.withPath(p_248521_ -> "block/" + p_248521_ + textureSuffix);
    }

    public static ResourceLocation getItemTexture(Item item) {
        ResourceLocation resourcelocation = BuiltInRegistries.ITEM.getKey(item);
        return resourcelocation.withPrefix("item/");
    }

    public static ResourceLocation getItemTexture(Item item, String textureSuffix) {
        ResourceLocation resourcelocation = BuiltInRegistries.ITEM.getKey(item);
        return resourcelocation.withPath(p_252192_ -> "item/" + p_252192_ + textureSuffix);
    }
}
