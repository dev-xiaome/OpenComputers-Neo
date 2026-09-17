package net.minecraft.world.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.apache.commons.lang3.Validate;

public class BannerItem extends StandingAndWallBlockItem {
    public BannerItem(Block block, Block wallBlock, Item.Properties properties) {
        super(block, wallBlock, properties, Direction.DOWN);
        Validate.isInstanceOf(AbstractBannerBlock.class, block);
        Validate.isInstanceOf(AbstractBannerBlock.class, wallBlock);
    }

    public static void appendHoverTextFromBannerBlockEntityTag(ItemStack stack, List<Component> tooltipComponents) {
        BannerPatternLayers bannerpatternlayers = stack.get(DataComponents.BANNER_PATTERNS);
        if (bannerpatternlayers != null) {
            for (int i = 0; i < Math.min(bannerpatternlayers.layers().size(), 6); i++) {
                BannerPatternLayers.Layer bannerpatternlayers$layer = bannerpatternlayers.layers().get(i);
                tooltipComponents.add(bannerpatternlayers$layer.description().withStyle(ChatFormatting.GRAY));
            }
        }
    }

    public DyeColor getColor() {
        return ((AbstractBannerBlock)this.getBlock()).getColor();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        appendHoverTextFromBannerBlockEntityTag(stack, tooltipComponents);
    }
}
