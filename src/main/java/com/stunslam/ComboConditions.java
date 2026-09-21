package com.stunslam;

import com.stunslam.config.Config;
import com.stunslam.util.InventoryUtils;
import com.stunslam.util.ShieldUtils;
import com.stunslam.util.TargetUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * The gate: can a macro run right now?
 *
 * Two entry points, one shared core. If any check fails the caller does nothing
 * and lets vanilla handle the input untouched. This class never scans for targets
 * on tick and never reacts to a shield going up; it only answers a question asked
 * by a click or a key press.
 */
public final class ComboConditions {

    private ComboConditions() {
    }

    /**
     * Stun slam: wins the click when its fall gate is satisfied.
     *
     * Because the fall gate lives here, a click that clears minFall is a stun slam
     * and a click that does not is available to the shield break. That is what
     * lets both macros be switched on at once without fighting: the height of the
     * player decides which one answers.
     */
    public static boolean canStartStunSlam(MinecraftClient mc) {
        if (!Config.stunSlamEnabled) {
            return false;
        }
        if (eligibleTarget(mc) == null) {
            return false;
        }
        if (InventoryUtils.findMace(mc.player.getInventory(), Config.maceType) < 0) {
            return false;
        }

        // The mace's own mechanic. Vanilla's smash predicate is
        //   fallDistance > 1.5 && !isGliding()
        // so a combo started from the ground would land a plain mace hit and waste
        // the shield break. Refusing to start until the player has already fallen
        // minFall blocks means the fall is still growing when the mace lands, so
        // the smash is earned rather than hoped for.
        if (Config.fallGateActive()) {
            if (mc.player.isOnGround() || mc.player.fallDistance < Config.minFall) {
                return false;
            }
        }
        return true;
    }

    /**
     * Shield break: fires on the same left-click as the stun slam, and takes the
     * click when it is switched on.
     *
     * Deliberately does not need a mace and has no fall requirement -- disabling a
     * raised shield needs only the axe swing, and demanding a fall would make it
     * useless on the ground where shields are usually raised.
     */
    public static boolean canStartShieldBreak(MinecraftClient mc) {
        if (!Config.shieldBreakEnabled) {
            return false;
        }
        return eligibleTarget(mc) != null;
    }

    /**
     * Checks both macros share: a live world, no screen open, and a shielding
     * living target in front of the crosshair with an axe somewhere in the hotbar.
     *
     * @return the target, or null if the shared preconditions are not met
     */
    private static Entity eligibleTarget(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            return null;
        }
        if (mc.currentScreen != null) {
            return null;
        }
        if (mc.player.isUsingItem()) {
            return null;
        }

        // Only a real left-click starts a macro. Both macros share the click, so
        // this guard has to live in the shared core rather than in one of them.
        //
        // MinecraftClient.doAttack() is private, but another mod can widen it with
        // an access widener and call it programmatically -- SpearSwapper does
        // exactly that on its G keybind. Without this check a macro would hijack
        // that call and fight the other mod for the hotbar. Vanilla itself only
        // reaches doAttack() from a branch that has already tested this same key,
        // so requiring it changes nothing for a genuine click.
        if (!mc.options.attackKey.isPressed()) {
            return null;
        }

        // Deliberately NO attack-cooldown check.
        //
        // Both macros drive their own hits through
        // ClientPlayerInteractionManager.attackEntity, which bypasses the vanilla
        // cooldown gate entirely, so requiring a full charge was never needed. It
        // only made the mod feel broken depending on what was held, because the
        // cooldown length is derived from the held item's attack speed: a mace is
        // 0.6 (33 ticks), an axe 1.0 (20 ticks), but an ordinary block is 4.0
        // (5 ticks). Holding an axe or mace therefore made the macro take four to
        // seven times longer to respond than holding anything else.

        Entity target = pickTarget(mc);
        if (!(target instanceof LivingEntity living)) {
            return null;
        }
        if (!ShieldUtils.isShielding(living)) {
            return null;
        }
        if (InventoryUtils.findAxe(mc.player.getInventory()) < 0) {
            return null;
        }
        return target;
    }

    /** The entity the crosshair is aiming at, or null. */
    public static Entity pickTarget(MinecraftClient mc) {
        return TargetUtils.crosshairTarget(mc);
    }
}
