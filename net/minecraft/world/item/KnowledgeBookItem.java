package net.minecraft.world.item;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public class KnowledgeBookItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    public KnowledgeBookItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * Called to trigger the item's "innate" right click behavior. To handle when this item is used on a Block, see {@link #onItemUse}.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        List<ResourceLocation> list = itemstack.getOrDefault(DataComponents.RECIPES, List.of());
        itemstack.consume(1, player);
        if (list.isEmpty()) {
            return InteractionResultHolder.fail(itemstack);
        } else {
            if (!level.isClientSide) {
                RecipeManager recipemanager = level.getServer().getRecipeManager();
                List<RecipeHolder<?>> list1 = new ArrayList<>(list.size());

                for (ResourceLocation resourcelocation : list) {
                    Optional<RecipeHolder<?>> optional = recipemanager.byKey(resourcelocation);
                    if (!optional.isPresent()) {
                        LOGGER.error("Invalid recipe: {}", resourcelocation);
                        return InteractionResultHolder.fail(itemstack);
                    }

                    list1.add(optional.get());
                }

                player.awardRecipes(list1);
                player.awardStat(Stats.ITEM_USED.get(this));
            }

            return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
        }
    }
}
