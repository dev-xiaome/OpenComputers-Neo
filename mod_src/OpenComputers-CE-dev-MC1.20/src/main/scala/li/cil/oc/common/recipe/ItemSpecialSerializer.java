package li.cil.oc.common.recipe;

import java.util.function.BiFunction;
import java.util.function.Function;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

public class ItemSpecialSerializer<T extends Recipe<?>> implements RecipeSerializer<T> {

    private final BiFunction<ResourceLocation, ItemLike, T> ctor;
    private final Function<T, Item> getter;

    public ItemSpecialSerializer(BiFunction<ResourceLocation, ItemLike, T> ctor, Function<T, Item> getter) {
        this.ctor = ctor;
        this.getter = getter;
    }

    @Override
    @NotNull
    public T fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
        ResourceLocation loc = ResourceLocation.tryParse(GsonHelper.getAsString(json, "item"));
        if (!ForgeRegistries.ITEMS.containsKey(loc)) {
            throw new JsonSyntaxException("Unknown item '" + loc + "'");
        }
        return ctor.apply(recipeId, ForgeRegistries.ITEMS.getValue(loc));
    }

    @Override
    public T fromNetwork(@NotNull ResourceLocation recipeId, FriendlyByteBuf buff) {
        return ctor.apply(recipeId, buff.readRegistryIdUnsafe(ForgeRegistries.ITEMS));
    }

    @Override
    public void toNetwork(FriendlyByteBuf buff, @NotNull T recipe) {
        buff.writeRegistryIdUnsafe(ForgeRegistries.ITEMS, getter.apply(recipe));
    }
}
