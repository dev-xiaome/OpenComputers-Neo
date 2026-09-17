package net.minecraft.world.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

public abstract class ProjectileWeaponItem extends Item {
    public static final Predicate<ItemStack> ARROW_ONLY = p_43017_ -> p_43017_.is(ItemTags.ARROWS);
    public static final Predicate<ItemStack> ARROW_OR_FIREWORK = ARROW_ONLY.or(p_43015_ -> p_43015_.is(Items.FIREWORK_ROCKET));

    public ProjectileWeaponItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * @deprecated Use ItemStack sensitive version {@link ProjectileWeaponItem#getSupportedHeldProjectiles(ItemStack)}
     */
    @Deprecated
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return this.getAllSupportedProjectiles();
    }

    /**
     * @deprecated Use ItemStack sensitive version {@link ProjectileWeaponItem#getAllSupportedProjectiles(ItemStack)}
     */
    @Deprecated
    public abstract Predicate<ItemStack> getAllSupportedProjectiles();

    /**
     * Override this method if the weapon stack allows special projectile that would only be used if it's in hand.
     * The default return value is a union-predicate of {@link ProjectileWeaponItem#getAllSupportedProjectiles(ItemStack)}
     * and {@link ProjectileWeaponItem#getSupportedHeldProjectiles()}
     *
     * @param stack The ProjectileWeapon stack
     * @return A predicate that returns true for supported projectile stack in hand
     */
    public Predicate<ItemStack> getSupportedHeldProjectiles(ItemStack stack) {
        return getAllSupportedProjectiles(stack).or(getSupportedHeldProjectiles());
    }

    /**
     * Override this method if the allowed projectile is weapon stack dependent.
     *
     * @param stack The ProjectileWeapon stack
     * @return A predicate that returns true for all supported projectile stack
     */
    public Predicate<ItemStack> getAllSupportedProjectiles(ItemStack stack) {
        return getAllSupportedProjectiles();
    }

    public static ItemStack getHeldProjectile(LivingEntity shooter, Predicate<ItemStack> isAmmo) {
        if (isAmmo.test(shooter.getItemInHand(InteractionHand.OFF_HAND))) {
            return shooter.getItemInHand(InteractionHand.OFF_HAND);
        } else {
            return isAmmo.test(shooter.getItemInHand(InteractionHand.MAIN_HAND)) ? shooter.getItemInHand(InteractionHand.MAIN_HAND) : ItemStack.EMPTY;
        }
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }

    public abstract int getDefaultProjectileRange();

    protected void shoot(
        ServerLevel level,
        LivingEntity shooter,
        InteractionHand hand,
        ItemStack weapon,
        List<ItemStack> projectileItems,
        float velocity,
        float inaccuracy,
        boolean isCrit,
        @Nullable LivingEntity target
    ) {
        float f = EnchantmentHelper.processProjectileSpread(level, weapon, shooter, 0.0F);
        float f1 = projectileItems.size() == 1 ? 0.0F : 2.0F * f / (float)(projectileItems.size() - 1);
        float f2 = (float)((projectileItems.size() - 1) % 2) * f1 / 2.0F;
        float f3 = 1.0F;

        for (int i = 0; i < projectileItems.size(); i++) {
            ItemStack itemstack = projectileItems.get(i);
            if (!itemstack.isEmpty()) {
                float f4 = f2 + f3 * (float)((i + 1) / 2) * f1;
                f3 = -f3;
                Projectile projectile = this.createProjectile(level, shooter, weapon, itemstack, isCrit);
                this.shootProjectile(shooter, projectile, i, velocity, inaccuracy, f4, target);
                level.addFreshEntity(projectile);
                weapon.hurtAndBreak(this.getDurabilityUse(itemstack), shooter, LivingEntity.getSlotForHand(hand));
                if (weapon.isEmpty()) {
                    break;
                }
            }
        }
    }

    protected int getDurabilityUse(ItemStack stack) {
        return 1;
    }

    protected abstract void shootProjectile(
        LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy, float angle, @Nullable LivingEntity target
    );

    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit) {
        ArrowItem arrowitem = ammo.getItem() instanceof ArrowItem arrowitem1 ? arrowitem1 : (ArrowItem)Items.ARROW;
        AbstractArrow abstractarrow = arrowitem.createArrow(level, ammo, shooter, weapon);
        if (isCrit) {
            abstractarrow.setCritArrow(true);
        }

        return customArrow(abstractarrow, ammo, weapon);
    }

    protected static List<ItemStack> draw(ItemStack weapon, ItemStack ammo, LivingEntity shooter) {
        if (ammo.isEmpty()) {
            return List.of();
        } else {
            int i = shooter.level() instanceof ServerLevel serverlevel ? EnchantmentHelper.processProjectileCount(serverlevel, weapon, shooter, 1) : 1;
            List<ItemStack> list = new ArrayList<>(i);
            ItemStack itemstack1 = ammo.copy();

            for (int j = 0; j < i; j++) {
                ItemStack itemstack = useAmmo(weapon, j == 0 ? ammo : itemstack1, shooter, j > 0);
                if (!itemstack.isEmpty()) {
                    list.add(itemstack);
                }
            }

            return list;
        }
    }

    protected static ItemStack useAmmo(ItemStack weapon, ItemStack ammo, LivingEntity shooter, boolean intangable) {
        // Neo: Adjust this check to respect ArrowItem#isInfinite, bypassing processAmmoUse if true.
        int i = !intangable && shooter.level() instanceof ServerLevel serverlevel && !(shooter.hasInfiniteMaterials() || (ammo.getItem() instanceof ArrowItem ai && ai.isInfinite(ammo, weapon, shooter)))
            ? EnchantmentHelper.processAmmoUse(serverlevel, weapon, ammo, 1)
            : 0;
        if (i > ammo.getCount()) {
            return ItemStack.EMPTY;
        } else if (i == 0) {
            ItemStack itemstack1 = ammo.copyWithCount(1);
            itemstack1.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
            return itemstack1;
        } else {
            ItemStack itemstack = ammo.split(i);
            if (ammo.isEmpty() && shooter instanceof Player player) {
                player.getInventory().removeItem(ammo);
            }

            return itemstack;
        }
    }

    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        return arrow;
    }

    /**
     * Neo: Controls what ammo ItemStack that Creative Mode should return if the player has no valid ammo in inventory.
     * Modded weapons should override this to return their own ammo if they do not use vanilla arrows.
     * @param player The player (if in context) firing the weapon
     * @param projectileWeaponItem The weapon ItemStack the ammo is for
     * @return The default ammo ItemStack for this weapon
     */
    public ItemStack getDefaultCreativeAmmo(@Nullable Player player, ItemStack projectileWeaponItem) {
        return Items.ARROW.getDefaultInstance();
    }
}
