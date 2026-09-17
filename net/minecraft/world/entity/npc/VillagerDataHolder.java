package net.minecraft.world.entity.npc;

import net.minecraft.world.entity.VariantHolder;

public interface VillagerDataHolder extends VariantHolder<VillagerType> {
    VillagerData getVillagerData();

    void setVillagerData(VillagerData data);

    default VillagerType getVariant() {
        return this.getVillagerData().getType();
    }

    default void setVariant(VillagerType variant) {
        this.setVillagerData(this.getVillagerData().setType(variant));
    }
}
