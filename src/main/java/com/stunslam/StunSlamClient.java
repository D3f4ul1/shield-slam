package com.stunslam;

import com.stunslam.config.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint.
 *
 * Registers one keybind and one tick hook. The tick hook drives the shield-break
 * toggle key, the macro controller and the config debounce. There is no second
 * tick hook anywhere in the mod.
 *
 * Only the panel key is registered with the game. The shield-break key is polled
 * directly so it never appears in Options -> Controls; it is captured and edited
 * inside the mod panel.
 *
 * Both macros are triggered by a normal left-click on a shielding target. The
 * shield-break key only switches which of the two answers that click.
 */
public class StunSlamClient implements ClientModInitializer {

    public static final String MOD_ID = "stunslam";

    /**
     * Display name for the panel keybind.
     *
     * A plain string rather than a translation key: this mod ships exactly the
     * files the spec lists and deliberately has no lang file, so a translation
     * key would render in the controls screen as the raw key text. Rebindable
     * from Options -> Controls.
     */
    public static final String KEY_OPEN_GUI = "Shield Slam: open panel (Right Shift)";

    private static KeyBinding openGuiKey;
    private static boolean shieldToggleHeld = false;

    @Override
    public void onInitializeClient() {
        Config.load();

        // MAPPING: in 1.21.11 the KeyBinding constructor takes a
        // KeyBinding.Category record, not a String translation key. The public
        // factory Category.create(Identifier) appends the category to the global
        // CATEGORIES list and throws IllegalArgumentException if that id is
        // already registered, so it must be called exactly once. Guarded below so
        // a hot reload cannot trip that throw.
        KeyBinding.Category category = createCategoryOnce();

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                KEY_OPEN_GUI,
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                client.setScreen(new StunSlamScreen());
            }
            pollShieldToggleKey(client);
            ComboController.tick(client);
            Config.tickDebouncedSave();
        });

        // Flush a pending debounced write on exit. Without this, quitting inside
        // the 250ms debounce window would lose the last change and break the
        // config round-trip.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Config.saveNow());
    }

    /**
     * Polls the shield-break toggle key.
     *
     * It is not a registered KeyBinding, so it never shows up in the vanilla
     * controls list -- the bind lives entirely in the mod panel. Edge-triggered,
     * so holding the key flips the switch once rather than every tick.
     *
     * MAPPING: InputUtil.isKeyPressed takes a Window, not a GLFW handle, in
     * 1.21.11. It resolves to glfwGetKey(window.getHandle(), key) == GLFW_PRESS.
     */
    private static void pollShieldToggleKey(MinecraftClient client) {
        if (!Config.shieldBreakBound() || client.player == null || client.currentScreen != null) {
            shieldToggleHeld = false;
            return;
        }
        boolean down = InputUtil.isKeyPressed(client.getWindow(), Config.shieldBreakKey);
        if (down && !shieldToggleHeld) {
            Config.toggleShieldBreak();
            // Action bar, so toggling does not spam the chat log.
            client.player.sendMessage(Text.literal(Config.shieldStateLabel()), true);
        }
        shieldToggleHeld = down;
    }

    private static KeyBinding.Category categoryCache;

    private static KeyBinding.Category createCategoryOnce() {
        if (categoryCache == null) {
            categoryCache = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        }
        return categoryCache;
    }
}
