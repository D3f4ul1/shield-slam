package com.stunslam.util;

import com.stunslam.config.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Hotbar lookups and slot swapping.
 *
 * The hotbar is slots 0-8 of {@link PlayerInventory}. Swapping always tells the
 * server: an unacknowledged swap means the server still thinks the old item is
 * held, and the whole combo depends on the server seeing the axe and the mace.
 */
public final class InventoryUtils {

    public static final int HOTBAR_SIZE = 9;

    private InventoryUtils() {
    }

    /** Index of the first axe in the hotbar, or -1. */
    public static int findAxe(PlayerInventory inv) {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack stack = inv.getStack(i);
            if (isAxe(stack)) {
                return i;
            }
        }
        return -1;
    }

    /** Index of the first mace in the hotbar that satisfies {@code type}, or -1. */
    public static int findMace(PlayerInventory inv, Config.MaceType type) {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack stack = inv.getStack(i);
            if (isAcceptableMace(stack, type)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isAxe(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof AxeItem;
    }

    /** True when the stack is a mace the combo is allowed to swing. */
    public static boolean isAcceptableMace(ItemStack stack, Config.MaceType type) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.MACE)) {
            return false;
        }
        if (type == Config.MaceType.ANY) {
            return true;
        }
        RegistryKey<Enchantment> wanted = type == Config.MaceType.DENSITY
                ? Enchantments.DENSITY
                : Enchantments.BREACH;

        // MAPPING: Enchantments.DENSITY / BREACH are RegistryKey<Enchantment> in
        // 1.21.11, while ItemEnchantmentsComponent.getLevel needs a
        // RegistryEntry<Enchantment>. RegistryEntry.matchesKey(RegistryKey) bridges
        // the two without a registry lookup.
        ItemEnchantmentsComponent enchants = stack.getEnchantments();
        for (RegistryEntry<Enchantment> entry : enchants.getEnchantments()) {
            if (entry.matchesKey(wanted) && enchants.getLevel(entry) > 0) {
                return true;
            }
        }
        return false;
    }

    /** The stack currently held in the player's selected hotbar slot. */
    public static ItemStack selectedStack(PlayerInventory inv) {
        return inv.getStack(inv.getSelectedSlot());
    }

    /**
     * Selects a hotbar slot and sends the swap to the server.
     *
     * MAPPING: the spec called PlayerInventory.selectedSlot a public int field.
     * In Yarn 1.21.11 build.6 it is {@code private int selectedSlot} and must be
     * reached through the public getSelectedSlot() / setSelectedSlot(int)
     * accessors. MinecraftClient's network handler is likewise reached through
     * the public getNetworkHandler() accessor (its backing field is private).
     */
    public static void swapSlot(MinecraftClient mc, int slot) {
        if (slot < 0 || slot >= HOTBAR_SIZE || mc.player == null) {
            return;
        }
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }
}
