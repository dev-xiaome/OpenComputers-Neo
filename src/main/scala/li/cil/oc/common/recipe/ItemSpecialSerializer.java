package li.cil.oc.common.recipe;

import java.util.function.BiFunction;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;

/// 「只带一个物品参数」的特殊配方序列化器（着色 / 去色）。
///
/// 1.21.1 适配：`RecipeSerializer` 由 `fromJson`/`fromNetwork`/`toNetwork` 改成
/// [MapCodec] + [StreamCodec]；配方不再自带 id（由数据包与注册表决定），
/// 因此构造器收到的 id 参数固定传 `null`（OCCE 的配方实现只保留该参数以兼容调用点）。
public class ItemSpecialSerializer<T extends Recipe<?>> implements RecipeSerializer<T> {

    private static final Codec<Item> ITEM_CODEC = BuiltInRegistries.ITEM.byNameCodec();

    private final BiFunction<ResourceLocation, ItemLike, T> ctor;
    private final Function<T, Item> getter;
    private final MapCodec<T> codec;
    private final StreamCodec<RegistryFriendlyByteBuf, T> streamCodec;

    public ItemSpecialSerializer(BiFunction<ResourceLocation, ItemLike, T> ctor, Function<T, Item> getter) {
        this.ctor = ctor;
        this.getter = getter;
        this.codec = ITEM_CODEC.fieldOf("item").xmap(
                item -> ctor.apply(null, item),
                recipe -> getter.apply(recipe));
        this.streamCodec = ByteBufCodecs.fromCodecWithRegistries(ITEM_CODEC).map(
                item -> ctor.apply(null, item),
                recipe -> getter.apply(recipe));
    }

    @Override
    public MapCodec<T> codec() {
        return codec;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
        return streamCodec;
    }
}
