package com.stunslam;

import com.stunslam.config.Config;
import com.stunslam.util.InventoryUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * The single screen. No tabs, no settings editor, no tooltips.
 *
 * Opens with Right Shift. Every change writes to Config immediately; Config
 * debounce-saves 250ms after the last write.
 *
 * Vanilla's own screen background is left intact so the panel sits on the usual
 * blurred world, and a translucent card is drawn over it to keep the rows
 * readable. The card's accent colour follows the master switch: green while the
 * mod is on, grey while it is off.
 */
public class StunSlamScreen extends Screen {

    private static final String TITLE = "\u2716 SHIELD SLAM \u2716";

    private static final int PANEL_W = 410;
    private static final int PANEL_H = 214;

    private static final int MARGIN = 20;
    private static final int COL_W = 175;
    private static final int COL_GAP = 20;

    /** Translucent so the blurred world still reads through, as vanilla screens do. */
    private static final int COLOR_PANEL_ON = 0xB0101A10;
    private static final int COLOR_PANEL_OFF = 0xB0101010;
    private static final int COLOR_ON = 0xFF3FBF3F;
    private static final int COLOR_OFF = 0xFF9A9A9A;
    private static final int COLOR_BAD = 0xFFFF5555;
    private static final int COLOR_WARN = 0xFFFFAA33;
    private static final int COLOR_DIVIDER = 0x60FFFFFF;

    private static final int ROW_H = 20;

    // Row offsets from the panel's top edge.
    private static final int Y_TITLE = 12;
    private static final int Y_SEPARATOR = 28;
    private static final int Y_STATUS = 40;
    private static final int Y_HEADERS = 60;
    private static final int Y_ROW1 = 76;
    private static final int Y_ROW2 = 100;
    private static final int Y_ROW3 = 124;
    private static final int Y_ROW4 = 148;
    private static final int Y_CLOSE = 176;

    private ButtonWidget shieldKeyButton;
    private boolean awaitingKey = false;

    public StunSlamScreen() {
        super(Text.literal("Stun Slam"));
    }

    /** The accent colour for every themed element on the panel. */
    private static int theme() {
        return Config.anyMacroEnabled() ? COLOR_ON : COLOR_OFF;
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        int leftX = px + MARGIN;
        int rightX = leftX + COL_W + COL_GAP;

        // Left column: stun slam.
        addDrawableChild(ButtonWidget
                .builder(Text.literal(stateButtonLabel()), b -> {
                    Config.toggleStunSlam();
                    b.setMessage(Text.literal(stateButtonLabel()));
                })
                .dimensions(leftX, py + Y_ROW1, COL_W, ROW_H)
                .build());

        addDrawableChild(new ValueSlider(leftX, py + Y_ROW2, COL_W, ROW_H,
                Config.DELAY_MIN_MS, Config.DELAY_MAX_MS, Config.DELAY_STEP_MS,
                Config.delayMs,
                v -> Config.setDelayMs((int) v),
                v -> Config.delayLabel((int) v)));

        addDrawableChild(new ValueSlider(leftX, py + Y_ROW3, COL_W, ROW_H,
                Config.MIN_FALL_MIN, Config.MIN_FALL_MAX, Config.MIN_FALL_STEP,
                Config.minFall,
                Config::setMinFall,
                Config::fallLabel));

        addDrawableChild(ButtonWidget
                .builder(Text.literal(Config.maceLabel()), b -> {
                    Config.cycleMaceType();
                    b.setMessage(Text.literal(Config.maceLabel()));
                })
                .dimensions(leftX, py + Y_ROW4, COL_W, ROW_H)
                .build());

        // Right column: shield break.
        addDrawableChild(ButtonWidget
                .builder(Text.literal(Config.shieldStateLabel()), b -> {
                    Config.toggleShieldBreak();
                    b.setMessage(Text.literal(Config.shieldStateLabel()));
                })
                .dimensions(rightX, py + Y_ROW1, COL_W, ROW_H)
                .build());

        addDrawableChild(new ValueSlider(rightX, py + Y_ROW2, COL_W, ROW_H,
                Config.DELAY_MIN_MS, Config.DELAY_MAX_MS, Config.DELAY_STEP_MS,
                Config.shieldBreakDelayMs,
                v -> Config.setShieldBreakDelayMs((int) v),
                v -> Config.shieldDelayLabel((int) v)));

        shieldKeyButton = addDrawableChild(ButtonWidget
                .builder(Text.literal(shieldKeyLabel()), b -> {
                    awaitingKey = !awaitingKey;
                    b.setMessage(Text.literal(shieldKeyLabel()));
                })
                .dimensions(rightX, py + Y_ROW3, COL_W, ROW_H)
                .build());

        addDrawableChild(ButtonWidget
                .builder(Text.literal("Close"), b -> this.close())
                .dimensions(leftX, py + Y_CLOSE, PANEL_W - MARGIN * 2, ROW_H)
                .build());
    }

    private static String stateButtonLabel() {
        return "Stun Slam: " + Config.stunSlamStateLabel();
    }

    /** Label for the shield-break key button, including the capture prompt. */
    private String shieldKeyLabel() {
        if (awaitingKey) {
            return "Break key: press...";
        }
        if (!Config.shieldBreakBound()) {
            return "Break key: UNBOUND";
        }
        // MAPPING: InputUtil.fromKeyCode takes a KeyInput record in 1.21.11,
        // not the (keyCode, scanCode) int pair older versions used.
        InputUtil.Key key = InputUtil.fromKeyCode(new KeyInput(Config.shieldBreakKey, 0, 0));
        return "Break key: " + key.getLocalizedText().getString();
    }

    /**
     * Captures the shield-break key while the button is armed.
     *
     * MAPPING: the 1.21.11 signature is keyPressed(KeyInput), not the
     * (int keyCode, int scanCode, int modifiers) triple older versions used.
     */
    @Override
    public boolean keyPressed(KeyInput input) {
        if (awaitingKey) {
            int code = input.key();
            if (code == GLFW.GLFW_KEY_ESCAPE) {
                Config.clearShieldBreakKey();
            } else {
                Config.setShieldBreakKey(code);
            }
            awaitingKey = false;
            if (shieldKeyButton != null) {
                shieldKeyButton.setMessage(Text.literal(shieldKeyLabel()));
            }
            return true;
        }
        return super.keyPressed(input);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * Vanilla's screen background is deliberately left alone here: it draws the
     * blurred, darkened world that every Minecraft menu sits on. The translucent
     * card below is what keeps the rows readable on top of it.
     */
    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        drawCard(context);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void drawCard(DrawContext context) {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        int right = px + PANEL_W;
        int bottom = py + PANEL_H;
        int accent = theme();

        context.fill(px, py, right, bottom,
                Config.anyMacroEnabled() ? COLOR_PANEL_ON : COLOR_PANEL_OFF);

        context.fill(px, py, right, py + 2, accent);
        context.fill(px, bottom - 2, right, bottom, accent);
        context.fill(px, py, px + 2, bottom, accent);
        context.fill(right - 2, py, right, bottom, accent);

        context.drawCenteredTextWithShadow(this.textRenderer, TITLE,
                px + PANEL_W / 2, py + Y_TITLE, accent);

        context.fill(px + MARGIN, py + Y_SEPARATOR, right - MARGIN, py + Y_SEPARATOR + 1,
                COLOR_DIVIDER);

        String status = statusText();
        context.drawCenteredTextWithShadow(this.textRenderer, status,
                px + PANEL_W / 2, py + Y_STATUS, statusColor(status));

        // Column headers.
        int leftX = px + MARGIN;
        int rightX = leftX + COL_W + COL_GAP;
        context.drawCenteredTextWithShadow(this.textRenderer, "STUN SLAM",
                leftX + COL_W / 2, py + Y_HEADERS, accent);
        context.drawCenteredTextWithShadow(this.textRenderer, "SHIELD BREAK",
                rightX + COL_W / 2, py + Y_HEADERS, accent);
    }

    private static int statusColor(String status) {
        switch (status) {
            case "STUN SLAM":
            case "SHIELD BREAK":
                return COLOR_ON;
            case "NEED FALL":
                return COLOR_WARN;
            case "BOTH OFF":
                return COLOR_OFF;
            default:
                return COLOR_BAD;
        }
    }

    /**
     * What a left-click would do right now, which is also the quickest way to see
     * why it would not do anything.
     */
    private static String statusText() {
        if (!Config.anyMacroEnabled()) {
            return "BOTH OFF";
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return "NO AXE";
        }
        PlayerInventory inv = mc.player.getInventory();
        if (InventoryUtils.findAxe(inv) < 0) {
            return "NO AXE";
        }

        // The stun slam is tested first because its fall gate is what separates the
        // two macros. Matches the order in ComboController.tryStart.
        boolean slamArmed = Config.stunSlamEnabled
                && InventoryUtils.findMace(inv, Config.maceType) >= 0
                && fallSatisfied(mc);
        if (slamArmed) {
            return "STUN SLAM";
        }
        if (Config.shieldBreakEnabled) {
            return "SHIELD BREAK";
        }
        // Only the stun slam is on and it is not armed yet, so say what is missing.
        if (InventoryUtils.findMace(inv, Config.maceType) < 0) {
            return "NO MACE";
        }
        return "NEED FALL";
    }

    /** Mirrors the stun slam's start condition in ComboConditions. */
    private static boolean fallSatisfied(MinecraftClient mc) {
        if (!Config.fallGateActive()) {
            return true;
        }
        return !mc.player.isOnGround() && mc.player.fallDistance >= Config.minFall;
    }

    // ------------------------------------------------------------------
    // Slider
    // ------------------------------------------------------------------

    /**
     * A vanilla slider bound to one numeric Config field.
     *
     * The slider's normalised 0..1 position maps onto the setting's range and
     * snaps to its step. The label comes from the slider's live position so it
     * tracks the drag; the setting is committed through applyValue(), which
     * SliderWidget calls on every change.
     */
    private static final class ValueSlider extends SliderWidget {

        private final double min;
        private final double max;
        private final double step;
        private final DoubleConsumer setter;
        private final DoubleFunction<String> labeller;

        ValueSlider(int x, int y, int width, int height,
                    double min, double max, double step, double initial,
                    DoubleConsumer setter, DoubleFunction<String> labeller) {
            super(x, y, width, height, Text.literal(""), Config.toSlider(initial, min, max));
            this.min = min;
            this.max = max;
            this.step = step;
            this.setter = setter;
            this.labeller = labeller;
            updateMessage();
        }

        /** The slider's position expressed in the setting's own units. */
        private double currentValue() {
            double v = this.min + this.value * (this.max - this.min);
            return Math.round(v / this.step) * this.step;
        }

        @Override
        protected void updateMessage() {
            // SliderWidget's constructor does not call this, but guard anyway so
            // the override stays safe if that ever changes.
            if (this.labeller == null) {
                return;
            }
            setMessage(Text.literal(this.labeller.apply(currentValue())));
        }

        @Override
        protected void applyValue() {
            if (this.setter != null) {
                this.setter.accept(currentValue());
            }
        }
    }
}
