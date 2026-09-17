package net.minecraft.world.item.trading;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class MerchantOffer {
    public static final Codec<MerchantOffer> CODEC = RecordCodecBuilder.create(
        p_324269_ -> p_324269_.group(
                    ItemCost.CODEC.fieldOf("buy").forGetter(p_330121_ -> p_330121_.baseCostA),
                    ItemCost.CODEC.lenientOptionalFieldOf("buyB").forGetter(p_330120_ -> p_330120_.costB),
                    ItemStack.CODEC.fieldOf("sell").forGetter(p_324095_ -> p_324095_.result),
                    Codec.INT.lenientOptionalFieldOf("uses", Integer.valueOf(0)).forGetter(p_324003_ -> p_324003_.uses),
                    Codec.INT.lenientOptionalFieldOf("maxUses", Integer.valueOf(4)).forGetter(p_323849_ -> p_323849_.maxUses),
                    Codec.BOOL.lenientOptionalFieldOf("rewardExp", Boolean.valueOf(true)).forGetter(p_323485_ -> p_323485_.rewardExp),
                    Codec.INT.lenientOptionalFieldOf("specialPrice", Integer.valueOf(0)).forGetter(p_324423_ -> p_324423_.specialPriceDiff),
                    Codec.INT.lenientOptionalFieldOf("demand", Integer.valueOf(0)).forGetter(p_324040_ -> p_324040_.demand),
                    Codec.FLOAT.lenientOptionalFieldOf("priceMultiplier", Float.valueOf(0.0F)).forGetter(p_323953_ -> p_323953_.priceMultiplier),
                    Codec.INT.lenientOptionalFieldOf("xp", Integer.valueOf(1)).forGetter(p_324202_ -> p_324202_.xp)
                )
                .apply(p_324269_, MerchantOffer::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MerchantOffer> STREAM_CODEC = StreamCodec.of(
        MerchantOffer::writeToStream, MerchantOffer::createFromStream
    );
    /**
     * The first input for this offer.
     */
    private final ItemCost baseCostA;
    /**
     * The second input for this offer.
     */
    private final Optional<ItemCost> costB;
    /**
     * The output of this offer.
     */
    private final ItemStack result;
    private int uses;
    private final int maxUses;
    private final boolean rewardExp;
    private int specialPriceDiff;
    private int demand;
    private final float priceMultiplier;
    private final int xp;

    private MerchantOffer(
        ItemCost baseCostA,
        Optional<ItemCost> costB,
        ItemStack result,
        int uses,
        int maxUses,
        boolean rewardExp,
        int specialPriceDiff,
        int demand,
        float priceMultiplier,
        int xp
    ) {
        this.baseCostA = baseCostA;
        this.costB = costB;
        this.result = result;
        this.uses = uses;
        this.maxUses = maxUses;
        this.rewardExp = rewardExp;
        this.specialPriceDiff = specialPriceDiff;
        this.demand = demand;
        this.priceMultiplier = priceMultiplier;
        this.xp = xp;
    }

    public MerchantOffer(ItemCost baseCostA, ItemStack result, int maxUses, int xp, float priceMultiplier) {
        this(baseCostA, Optional.empty(), result, maxUses, xp, priceMultiplier);
    }

    public MerchantOffer(ItemCost baseCostA, Optional<ItemCost> costB, ItemStack result, int maxUses, int xp, float priceMultiplier) {
        this(baseCostA, costB, result, 0, maxUses, xp, priceMultiplier);
    }

    public MerchantOffer(ItemCost baseCostA, Optional<ItemCost> costB, ItemStack result, int uses, int maxUses, int xp, float priceMultiplier) {
        this(baseCostA, costB, result, uses, maxUses, xp, priceMultiplier, 0);
    }

    public MerchantOffer(
        ItemCost baseCostA, Optional<ItemCost> costB, ItemStack result, int uses, int maxUses, int xp, float priceMultiplier, int demand
    ) {
        this(baseCostA, costB, result, uses, maxUses, true, 0, demand, priceMultiplier, xp);
    }

    private MerchantOffer(MerchantOffer other) {
        this(
            other.baseCostA,
            other.costB,
            other.result.copy(),
            other.uses,
            other.maxUses,
            other.rewardExp,
            other.specialPriceDiff,
            other.demand,
            other.priceMultiplier,
            other.xp
        );
    }

    public ItemStack getBaseCostA() {
        return this.baseCostA.itemStack();
    }

    public ItemStack getCostA() {
        return this.baseCostA.itemStack().copyWithCount(this.getModifiedCostCount(this.baseCostA));
    }

    private int getModifiedCostCount(ItemCost itemCost) {
        int i = itemCost.count();
        int j = Math.max(0, Mth.floor((float)(i * this.demand) * this.priceMultiplier));
        return Mth.clamp(i + j + this.specialPriceDiff, 1, itemCost.itemStack().getMaxStackSize());
    }

    public ItemStack getCostB() {
        return this.costB.map(ItemCost::itemStack).orElse(ItemStack.EMPTY);
    }

    public ItemCost getItemCostA() {
        return this.baseCostA;
    }

    public Optional<ItemCost> getItemCostB() {
        return this.costB;
    }

    public ItemStack getResult() {
        return this.result;
    }

    public void updateDemand() {
        this.demand = this.demand + this.uses - (this.maxUses - this.uses);
    }

    public ItemStack assemble() {
        return this.result.copy();
    }

    public int getUses() {
        return this.uses;
    }

    public void resetUses() {
        this.uses = 0;
    }

    public int getMaxUses() {
        return this.maxUses;
    }

    public void increaseUses() {
        this.uses++;
    }

    public int getDemand() {
        return this.demand;
    }

    public void addToSpecialPriceDiff(int add) {
        this.specialPriceDiff += add;
    }

    public void resetSpecialPriceDiff() {
        this.specialPriceDiff = 0;
    }

    public int getSpecialPriceDiff() {
        return this.specialPriceDiff;
    }

    public void setSpecialPriceDiff(int price) {
        this.specialPriceDiff = price;
    }

    public float getPriceMultiplier() {
        return this.priceMultiplier;
    }

    public int getXp() {
        return this.xp;
    }

    public boolean isOutOfStock() {
        return this.uses >= this.maxUses;
    }

    public void setToOutOfStock() {
        this.uses = this.maxUses;
    }

    public boolean needsRestock() {
        return this.uses > 0;
    }

    public boolean shouldRewardExp() {
        return this.rewardExp;
    }

    public boolean satisfiedBy(ItemStack playerOfferA, ItemStack playerOfferB) {
        if (!this.baseCostA.test(playerOfferA) || playerOfferA.getCount() < this.getModifiedCostCount(this.baseCostA)) {
            return false;
        } else {
            return !this.costB.isPresent() ? playerOfferB.isEmpty() : this.costB.get().test(playerOfferB) && playerOfferB.getCount() >= this.costB.get().count();
        }
    }

    public boolean take(ItemStack playerOfferA, ItemStack playerOfferB) {
        if (!this.satisfiedBy(playerOfferA, playerOfferB)) {
            return false;
        } else {
            playerOfferA.shrink(this.getCostA().getCount());
            if (!this.getCostB().isEmpty()) {
                playerOfferB.shrink(this.getCostB().getCount());
            }

            return true;
        }
    }

    public MerchantOffer copy() {
        return new MerchantOffer(this);
    }

    private static void writeToStream(RegistryFriendlyByteBuf buffer, MerchantOffer offer) {
        ItemCost.STREAM_CODEC.encode(buffer, offer.getItemCostA());
        ItemStack.STREAM_CODEC.encode(buffer, offer.getResult());
        ItemCost.OPTIONAL_STREAM_CODEC.encode(buffer, offer.getItemCostB());
        buffer.writeBoolean(offer.isOutOfStock());
        buffer.writeInt(offer.getUses());
        buffer.writeInt(offer.getMaxUses());
        buffer.writeInt(offer.getXp());
        buffer.writeInt(offer.getSpecialPriceDiff());
        buffer.writeFloat(offer.getPriceMultiplier());
        buffer.writeInt(offer.getDemand());
    }

    public static MerchantOffer createFromStream(RegistryFriendlyByteBuf buffer) {
        ItemCost itemcost = ItemCost.STREAM_CODEC.decode(buffer);
        ItemStack itemstack = ItemStack.STREAM_CODEC.decode(buffer);
        Optional<ItemCost> optional = ItemCost.OPTIONAL_STREAM_CODEC.decode(buffer);
        boolean flag = buffer.readBoolean();
        int i = buffer.readInt();
        int j = buffer.readInt();
        int k = buffer.readInt();
        int l = buffer.readInt();
        float f = buffer.readFloat();
        int i1 = buffer.readInt();
        MerchantOffer merchantoffer = new MerchantOffer(itemcost, optional, itemstack, i, j, k, f, i1);
        if (flag) {
            merchantoffer.setToOutOfStock();
        }

        merchantoffer.setSpecialPriceDiff(l);
        return merchantoffer;
    }
}
