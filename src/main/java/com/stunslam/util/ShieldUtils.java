package com.stunslam.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Shield detection.
 *
 * A landed axe hit only disables a shield that is actually raised, so the combo
 * must distinguish "holding a shield" from "blocking with a shield".
 */
public final class ShieldUtils {

    private ShieldUtils() {
    }

    /** True when the entity is actively blocking with a shield. */
    public static boolean isShielding(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        // MAPPING: LivingEntity.isBlocking() / isUsingItem() / getActiveItem() all
        // still exist under these names in Yarn 1.21.11 build.6.
        if (!entity.isBlocking() || !entity.isUsingItem()) {
            return false;
        }
        ItemStack active = entity.getActiveItem();
        return !active.isEmpty() && active.isOf(Items.SHIELD);
    }
}
