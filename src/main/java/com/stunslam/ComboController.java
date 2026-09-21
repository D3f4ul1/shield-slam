package com.stunslam;

import com.stunslam.config.Config;
import com.stunslam.util.InventoryUtils;
import com.stunslam.util.TargetUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/**
 * The tick-scheduled macro sequence, shared by both macros.
 *
 * Five phases: swap to the axe, hit, swap to the mace, hit, restore the original
 * slot. The shield break stops after the axe hit.
 *
 * Both macros fire from the same trigger -- a left-click on a shielding target --
 * so {@link #tryStart} picks between them. The stun slam's fall gate is the
 * divider: a click that clears it is a stun slam, one that does not is a shield
 * break. The two switches are independent, so either can be on alone.
 *
 * A swap and the hit that uses it deliberately share a tick. That is safe because
 * ClientPlayerInteractionManager.attackEntity calls syncSelectedSlot() itself
 * before sending the attack packet, and Minecraft's channel is ordered, so the
 * server always sees the new item before the hit. Chaining them removes the dead
 * tick that used to sit between every swap and its swing, which is what left no
 * useful speeds between "one tick per step" and "everything at once".
 *
 * {@link Config#delayMs} is the pause held between the axe hit and the mace hit;
 * {@link Config#shieldBreakDelayMs} is the pause between the shield-break hit and
 * the restore. Either way the axe stays in hand for the whole pause where it can
 * be seen.
 *
 * There is no fall gate here; that lives in {@link ComboConditions} as a start
 * condition, so the stun slam never begins from the ground in the first place.
 */
public final class ComboController {

    public enum Phase {
        IDLE,
        SWAP_TO_AXE,
        HIT_AXE,
        SWAP_TO_MACE,
        HIT_MACE,
        SWAP_BACK
    }

    /**
     * Which macro is running.
     *
     * Both share the same state machine and the same opening axe swing; the shield
     * break simply stops after it, since disabling a raised shield is all it is for.
     */
    public enum Mode {
        STUN_SLAM,
        SHIELD_BREAK
    }

    private static Phase phase = Phase.IDLE;
    private static Mode mode = Mode.STUN_SLAM;
    private static int phaseTicks = 0;
    private static int savedSlot = -1;
    private static Entity target = null;

    /**
     * Bound on how many phases may chain inside one tick. The sequence has five,
     * so this only guarantees the loop terminates if a phase ever fails to advance.
     */
    private static final int MAX_CHAIN = 8;

    private ComboController() {
    }

    /** True while a sequence is in flight. */
    public static boolean isRunning() {
        return phase != Phase.IDLE;
    }

    public static Phase phase() {
        return phase;
    }

    /**
     * Called from the mixin on every left-click that vanilla wanted to turn into
     * an attack. Returns true if a macro took ownership of this click.
     *
     * Order matters. The stun slam's fall gate is what separates the two macros: a
     * click that clears {@link Config#minFall} is a stun slam, and one that does
     * not falls through to the shield break. Swapping these two tests would let the
     * shield break swallow every click and the stun slam would never run.
     */
    public static boolean tryStart(MinecraftClient mc) {
        if (phase != Phase.IDLE) {
            return false;
        }
        if (ComboConditions.canStartStunSlam(mc)) {
            return begin(mc, Mode.STUN_SLAM);
        }
        if (ComboConditions.canStartShieldBreak(mc)) {
            return begin(mc, Mode.SHIELD_BREAK);
        }
        return false;
    }

    private static boolean begin(MinecraftClient mc, Mode newMode) {
        // MAPPING: PlayerInventory.selectedSlot is private in 1.21.11 build.6;
        // read it through the public getSelectedSlot() accessor.
        savedSlot = mc.player.getInventory().getSelectedSlot();
        target = ComboConditions.pickTarget(mc);
        mode = newMode;
        phase = Phase.SWAP_TO_AXE;
        phaseTicks = 0;
        return true;
    }

    public static void tick(MinecraftClient mc) {
        if (phase == Phase.IDLE) {
            return;
        }
        // If the macro that is mid-sequence gets switched off, put the item back
        // and stop. Checks the running macro's own switch, not the other one's.
        boolean stillEnabled = mode == Mode.SHIELD_BREAK
                ? Config.shieldBreakEnabled
                : Config.stunSlamEnabled;
        if (!stillEnabled) {
            abort(mc);
            return;
        }
        // Opening a screen pauses the combo rather than letting it run blind
        // behind the GUI. Timers do not advance, so closing the screen resumes
        // the sequence exactly where it stopped.
        if (mc.currentScreen != null) {
            return;
        }
        if (mc.player == null || target == null || !target.isAlive()) {
            abort(mc);
            return;
        }

        // Run phases until one of them has to wait for the clock.
        for (int guard = 0; guard < MAX_CHAIN && phase != Phase.IDLE; guard++) {
            if (!advance(mc)) {
                break;
            }
        }
        phaseTicks++;
    }

    /**
     * Runs one phase transition.
     *
     * @return true when the next phase may also run inside this same tick
     */
    private static boolean advance(MinecraftClient mc) {
        switch (phase) {
            case SWAP_TO_AXE: {
                int axe = InventoryUtils.findAxe(mc.player.getInventory());
                if (axe < 0) {
                    abort(mc);
                    return false;
                }
                InventoryUtils.swapSlot(mc, axe);
                phase = Phase.HIT_AXE;
                return true;
            }

            case HIT_AXE: {
                // Safe in the same tick as the swap: attackEntity syncs the slot
                // before it sends the attack, and the channel is ordered, so the
                // server resolves this hit with the axe in hand and the shield
                // drops as intended.
                if (reselectIfWrongItem(mc, true)) {
                    return false;
                }
                if (!TargetUtils.inReach(mc, target)) {
                    abort(mc);
                    return false;
                }
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                if (mode == Mode.SHIELD_BREAK) {
                    // The axe swing is the whole shield break; stop here and let
                    // SWAP_BACK hold the axe for its pause.
                    phase = Phase.SWAP_BACK;
                    phaseTicks = 0;
                    return false;
                }
                phase = Phase.SWAP_TO_MACE;
                phaseTicks = 0;
                return true;
            }

            case SWAP_TO_MACE: {
                // The pause lives here, so the axe stays visible for its duration.
                if (phaseTicks < Config.delayTicks()) {
                    return false;
                }
                int mace = InventoryUtils.findMace(mc.player.getInventory(), Config.maceType);
                if (mace < 0) {
                    abort(mc);
                    return false;
                }
                InventoryUtils.swapSlot(mc, mace);
                phase = Phase.HIT_MACE;
                return true;
            }

            case HIT_MACE: {
                if (reselectIfWrongItem(mc, false)) {
                    return false;
                }
                if (!TargetUtils.inReach(mc, target)) {
                    abort(mc);
                    return false;
                }
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                phase = Phase.SWAP_BACK;
                return false;
            }

            case SWAP_BACK: {
                // In shield-break mode the pause lives here, so the axe stays in
                // hand and visible for its duration. The stun slam has already
                // spent its pause before the mace hit.
                if (mode == Mode.SHIELD_BREAK
                        && phaseTicks < Config.delayTicks(Config.shieldBreakDelayMs)) {
                    return false;
                }
                InventoryUtils.swapSlot(mc, savedSlot);
                reset();
                return false;
            }

            default:
                reset();
                return false;
        }
    }

    /**
     * Guards against the held item changing mid-phase.
     *
     * The pause before the mace swap is long enough for the player to scroll the
     * hotbar. If the selection moved, the swing would send an attack with the
     * wrong item in hand -- the axe would not drop the shield, or the mace hit
     * would be wasted. Re-selects the wanted item, or aborts if it is gone.
     *
     * @return true when the caller should stop for this tick
     */
    private static boolean reselectIfWrongItem(MinecraftClient mc, boolean wantAxe) {
        PlayerInventory inv = mc.player.getInventory();
        ItemStack held = InventoryUtils.selectedStack(inv);

        boolean correct = wantAxe
                ? InventoryUtils.isAxe(held)
                : InventoryUtils.isAcceptableMace(held, Config.maceType);
        if (correct) {
            return false;
        }

        int slot = wantAxe
                ? InventoryUtils.findAxe(inv)
                : InventoryUtils.findMace(inv, Config.maceType);
        if (slot < 0) {
            abort(mc);
            return true;
        }
        InventoryUtils.swapSlot(mc, slot);
        return true;
    }

    /** Puts the original item back and returns to IDLE. */
    public static void abort(MinecraftClient mc) {
        if (mc.player != null && savedSlot >= 0) {
            InventoryUtils.swapSlot(mc, savedSlot);
        }
        reset();
    }

    private static void reset() {
        phase = Phase.IDLE;
        mode = Mode.STUN_SLAM;
        phaseTicks = 0;
        target = null;
        savedSlot = -1;
    }
}
