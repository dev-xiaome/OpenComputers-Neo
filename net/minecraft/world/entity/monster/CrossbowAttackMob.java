package net.minecraft.world.entity.monster;

import javax.annotation.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public interface CrossbowAttackMob extends RangedAttackMob {
    void setChargingCrossbow(boolean chargingCrossbow);

    @Nullable
    LivingEntity getTarget();

    void onCrossbowAttackPerformed();

    default void performCrossbowAttack(LivingEntity user, float velocity) {
        InteractionHand interactionhand = ProjectileUtil.getWeaponHoldingHand(user, item -> item instanceof CrossbowItem);
        ItemStack itemstack = user.getItemInHand(interactionhand);
        if (itemstack.getItem() instanceof CrossbowItem crossbowitem) {
            crossbowitem.performShooting(
                user.level(), user, interactionhand, itemstack, velocity, (float)(14 - user.level().getDifficulty().getId() * 4), this.getTarget()
            );
        }

        this.onCrossbowAttackPerformed();
    }
}
