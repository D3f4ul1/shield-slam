package com.stunslam.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * One flat config class. Four settings, read directly by the combo and the GUI.
 *
 * Writes go through the setters, which clamp to range and mark the config
 * dirty. The debounce timer flushes to disk 250ms after the last write.
 */
public final class Config {

    /** Which mace is acceptable for the second hit. */
    public enum MaceType {
        ANY,
        DENSITY,
        BREACH;

        public MaceType next() {
            MaceType[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    // ------------------------------------------------------------------
    // Ranges
    // ------------------------------------------------------------------

    /**
     * Pause between the axe hit and the mace hit, in milliseconds.
     *
     * Minecraft's game logic runs in whole ticks at 20 per second, so 50ms is the
     * smallest real step; the slider moves in 50ms increments because anything
     * finer would be a lie. There is no burst mode below 0: the sequence always
     * takes at least two ticks, so the axe and the mace are each held on their own
     * tick and the swap is visible.
     */
    public static final int DELAY_MIN_MS = 0;
    public static final int DELAY_MAX_MS = 500;
    public static final int DELAY_STEP_MS = 50;

    /**
     * How far the player must already have fallen before the combo will start.
     *
     * This is the mace's own mechanic: vanilla only awards the smash bonus at
     * more than 1.5 blocks of fall, so the macro refuses to fire from the ground.
     * 0 disables the gate entirely.
     */
    public static final double MIN_FALL_MIN = 0.0D;
    public static final double MIN_FALL_MAX = 6.0D;
    public static final double MIN_FALL_STEP = 0.5D;

    public static final long SAVE_DEBOUNCE_MS = 250L;

    /** 0 means the shield break has no key bound and cannot fire. */
    public static final int KEY_UNBOUND = 0;

    // ------------------------------------------------------------------
    // Settings. Plain fields: the combo reads these directly.
    // ------------------------------------------------------------------

    /**
     * Whether the stun slam answers a left-click.
     *
     * Independent of the shield break: each macro has its own switch, and neither
     * can turn the other off.
     */
    public static boolean stunSlamEnabled = true;

    /** Pause between the axe hit and the mace hit, in milliseconds. */
    public static int delayMs = 100;

    /**
     * Minimum fall distance required before the stun slam will start. 0 = off.
     *
     * This doubles as the divider between the two macros. Vanilla only awards the
     * mace smash above 1.5 blocks of fall, so when both macros are switched on a
     * click that clears this threshold is a stun slam and one that does not is a
     * shield break.
     */
    public static double minFall = 1.5D;

    public static MaceType maceType = MaceType.ANY;

    /**
     * When on, a left-click on a shielding target performs the axe-only shield
     * break instead of the full stun slam. Flipped by its key or from the panel.
     */
    public static boolean shieldBreakEnabled = false;

    /**
     * GLFW key code that toggles the shield break. Captured in the mod panel
     * rather than registered with the game, so it never appears in
     * Options -> Controls.
     */
    public static int shieldBreakKey = GLFW.GLFW_KEY_V;

    /** Pause between the shield-break hit and restoring the original item, in ms. */
    public static int shieldBreakDelayMs = 100;

    // ------------------------------------------------------------------
    // Storage
    // ------------------------------------------------------------------

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static long dirtyAt = -1L;

    private Config() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("stunslam.json");
    }

    // ------------------------------------------------------------------
    // Setters. Each clamps to range and marks the config dirty.
    // ------------------------------------------------------------------

    public static void setStunSlamEnabled(boolean value) {
        stunSlamEnabled = value;
        markDirty();
    }

    public static void toggleStunSlam() {
        setStunSlamEnabled(!stunSlamEnabled);
    }

    public static void setDelayMs(int ms) {
        delayMs = clampInt(ms, DELAY_MIN_MS, DELAY_MAX_MS);
        markDirty();
    }

    public static void setMinFall(double blocks) {
        minFall = clampStep(blocks, MIN_FALL_MIN, MIN_FALL_MAX, MIN_FALL_STEP);
        markDirty();
    }

    public static void setMaceType(MaceType type) {
        maceType = type == null ? MaceType.ANY : type;
        markDirty();
    }

    public static void cycleMaceType() {
        setMaceType(maceType.next());
    }

    public static void setShieldBreakKey(int keyCode) {
        shieldBreakKey = Math.max(KEY_UNBOUND, keyCode);
        markDirty();
    }

    public static void clearShieldBreakKey() {
        setShieldBreakKey(KEY_UNBOUND);
    }

    public static void setShieldBreakEnabled(boolean value) {
        shieldBreakEnabled = value;
        markDirty();
    }

    public static void toggleShieldBreak() {
        setShieldBreakEnabled(!shieldBreakEnabled);
    }

    public static void setShieldBreakDelayMs(int ms) {
        shieldBreakDelayMs = clampInt(ms, DELAY_MIN_MS, DELAY_MAX_MS);
        markDirty();
    }

    // ------------------------------------------------------------------
    // Derived values and labels
    // ------------------------------------------------------------------

    /** True when the fall gate is doing anything. */
    public static boolean fallGateActive() {
        return minFall > 0.0D;
    }

    /** True when the shield break has a key to fire from. */
    public static boolean shieldBreakBound() {
        return shieldBreakKey != KEY_UNBOUND;
    }

    public static String maceLabel() {
        return "Mace: " + maceType.name();
    }

    public static String stunSlamStateLabel() {
        return stunSlamEnabled ? "ON" : "OFF";
    }

    public static String shieldStateLabel() {
        return "Shield break: " + (shieldBreakEnabled ? "ON" : "OFF");
    }

    /** True when at least one macro is switched on, for the panel's accent. */
    public static boolean anyMacroEnabled() {
        return stunSlamEnabled || shieldBreakEnabled;
    }

    /** Whole ticks the delay rounds up to, given the 20-tick game clock. */
    public static int delayTicks() {
        return delayTicks(delayMs);
    }

    public static int delayTicks(int ms) {
        return (ms + 49) / 50;
    }

    public static String delayLabel(int ms) {
        return "Delay: " + ms + " ms";
    }

    public static String shieldDelayLabel(int ms) {
        return "Break delay: " + ms + " ms";
    }

    public static String fallLabel(double blocks) {
        if (blocks <= 0.0D) {
            return "Min fall: OFF";
        }
        return String.format(Locale.ROOT, "Min fall: %.1f blocks", blocks);
    }

    // ------------------------------------------------------------------
    // Load / save
    // ------------------------------------------------------------------

    public static void load() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            saveNow();
            return;
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(raw);
            if (!root.isJsonObject()) {
                saveNow();
                return;
            }
            JsonObject o = root.getAsJsonObject();
            if (o.has("stunSlamEnabled")) {
                stunSlamEnabled = o.get("stunSlamEnabled").getAsBoolean();
            } else if (o.has("enabled")) {
                // Migration: earlier builds had one master switch that gated both
                // macros. Carry its value onto the stun slam so an upgrade does not
                // silently re-enable something the player had switched off.
                stunSlamEnabled = o.get("enabled").getAsBoolean();
            }
            if (o.has("delayMs")) {
                delayMs = clampInt(o.get("delayMs").getAsInt(), DELAY_MIN_MS, DELAY_MAX_MS);
            }
            if (o.has("minFall")) {
                minFall = clampStep(o.get("minFall").getAsDouble(),
                        MIN_FALL_MIN, MIN_FALL_MAX, MIN_FALL_STEP);
            }
            if (o.has("maceType")) {
                maceType = parseMaceType(o.get("maceType").getAsString());
            }
            if (o.has("shieldBreakKey")) {
                shieldBreakKey = Math.max(KEY_UNBOUND, o.get("shieldBreakKey").getAsInt());
            }
            if (o.has("shieldBreakEnabled")) {
                shieldBreakEnabled = o.get("shieldBreakEnabled").getAsBoolean();
            }
            if (o.has("shieldBreakDelayMs")) {
                shieldBreakDelayMs = clampInt(o.get("shieldBreakDelayMs").getAsInt(),
                        DELAY_MIN_MS, DELAY_MAX_MS);
            }
            // Older files carried stepTicks / ackDelayTicks / waitForCharge /
            // minFallDistance / maxWaitTicks / autoJump from earlier designs.
            // Unknown keys are ignored, so a stale config still loads cleanly and
            // is rewritten without them on the next save.
        } catch (Exception e) {
            // A corrupt config must never stop the mod from loading. Fall back to
            // defaults and rewrite the file so the next launch starts clean.
            stunSlamEnabled = true;
            delayMs = 100;
            minFall = 1.5D;
            maceType = MaceType.ANY;
            shieldBreakEnabled = false;
            shieldBreakKey = GLFW.GLFW_KEY_V;
            shieldBreakDelayMs = 100;
            saveNow();
        }
    }

    private static MaceType parseMaceType(String raw) {
        for (MaceType t : MaceType.values()) {
            if (t.name().equalsIgnoreCase(raw)) {
                return t;
            }
        }
        return MaceType.ANY;
    }

    private static void markDirty() {
        dirtyAt = System.currentTimeMillis();
    }

    /**
     * Called once per client tick. Writes to disk only after the last change has
     * been quiet for {@link #SAVE_DEBOUNCE_MS} milliseconds.
     */
    public static void tickDebouncedSave() {
        if (dirtyAt < 0L) {
            return;
        }
        if (System.currentTimeMillis() - dirtyAt < SAVE_DEBOUNCE_MS) {
            return;
        }
        dirtyAt = -1L;
        saveNow();
    }

    public static void saveNow() {
        JsonObject o = new JsonObject();
        o.addProperty("stunSlamEnabled", stunSlamEnabled);
        o.addProperty("delayMs", delayMs);
        o.addProperty("minFall", minFall);
        o.addProperty("maceType", maceType.name());
        o.addProperty("shieldBreakKey", shieldBreakKey);
        o.addProperty("shieldBreakEnabled", shieldBreakEnabled);
        o.addProperty("shieldBreakDelayMs", shieldBreakDelayMs);
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Nothing useful to do here. The combo keeps working with the
            // in-memory values even if the disk write fails.
        }
    }

    // ------------------------------------------------------------------
    // Math helpers
    // ------------------------------------------------------------------

    public static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static double clampStep(double v, double min, double max, double step) {
        double snapped = Math.round(v / step) * step;
        snapped = Math.max(min, Math.min(max, snapped));
        // Kill floating point dust so 3.5 does not print as 3.5000000001.
        return Math.round(snapped / step) * step;
    }

    /** Normalised 0..1 position of a value inside its range, for the sliders. */
    public static double toSlider(double value, double min, double max) {
        if (max <= min) {
            return 0.0D;
        }
        return (value - min) / (max - min);
    }
}
