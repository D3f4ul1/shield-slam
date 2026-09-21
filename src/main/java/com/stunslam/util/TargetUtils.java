package com.stunslam.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The entity under the crosshair, and a reach sanity check.
 */
public final class TargetUtils {

    /** Maximum distance the combo will engage a target at, in blocks. */
    public static final double REACH = 4.0D;

    /**
     * Slack allowed on the pre-hit reach check. The target can drift between the
     * swap and the hit; a small tolerance stops the combo aborting on a target
     * that stepped half a block sideways.
     */
    public static final double REACH_TOLERANCE = 1.0D;

    private TargetUtils() {
    }

    /**
     * The entity the crosshair is aiming at, or null.
     *
     * IMPORTANT — do not "simplify" this back into a raycast.
     * Entity.raycast(double, float, boolean) delegates to World.raycast and is
     * declared to return HitResult, but it can only ever produce a
     * BlockHitResult: its bytecode ends in
     * World.raycast(RaycastContext) -> BlockHitResult. A raycast built on it
     * always yields null here and the combo would never fire.
     *
     * MinecraftClient.crosshairTarget is the correct source. GameRenderer fills
     * it every frame with a proper entity raycast, and vanilla doAttack() reads
     * this exact field to decide what to hit — so the combo engages precisely
     * what the player is looking at.
     */
    public static Entity crosshairTarget(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            return null;
        }
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof EntityHitResult entityHit)) {
            return null;
        }
        Entity entity = entityHit.getEntity();
        if (entity == null || !entity.isAlive()) {
            return null;
        }
        // Vanilla targets at the attack-range component's distance, which can
        // exceed the 4.0 blocks this macro is specified to work at.
        return withinReach(mc, entity, REACH) ? entity : null;
    }

    /** True when the target is still within reach of the player's eyes. */
    public static boolean inReach(MinecraftClient mc, Entity target) {
        if (mc.player == null || target == null || !target.isAlive()) {
            return false;
        }
        return withinReach(mc, target, REACH + REACH_TOLERANCE);
    }

    /**
     * Measures eye-to-hitbox distance to the nearest point of the target's box
     * rather than its centre, so tall or wide targets are not penalised.
     *
     * Entity.getEyePos() and getBoundingBox() are unchanged in 1.21.11.
     */
    private static boolean withinReach(MinecraftClient mc, Entity target, double limit) {
        Vec3d eye = mc.player.getEyePos();
        Box box = target.getBoundingBox();

        double dx = Math.max(0.0D, Math.max(box.minX - eye.x, eye.x - box.maxX));
        double dy = Math.max(0.0D, Math.max(box.minY - eye.y, eye.y - box.maxY));
        double dz = Math.max(0.0D, Math.max(box.minZ - eye.z, eye.z - box.maxZ));

        return dx * dx + dy * dy + dz * dz <= limit * limit;
    }
}
