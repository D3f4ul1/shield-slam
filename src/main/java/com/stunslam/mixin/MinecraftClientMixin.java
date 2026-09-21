package com.stunslam.mixin;

import com.stunslam.ComboController;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The only hook in the mod.
 *
 * MAPPING: in Yarn 1.21.11 build.6 MinecraftClient.doAttack() is still
 * {@code private boolean doAttack()} with no parameters, so the spec's signature
 * holds. It is called from handleInputEvents() on the ticks where the left mouse
 * is held and the attack cooldown is ready.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void onDoAttack(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = (MinecraftClient) (Object) this;

        if (ComboController.tryStart(mc)) {
            // Pretend vanilla attacked: returning true makes the caller consume
            // the click and start the attack cooldown, so the raw swing never
            // reaches the server.
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }

        // The combo is already in flight and owns the attack input. Swallow the
        // click rather than falling through.
        //
        // The spec's snippet only cancels when tryStart() succeeds, which leaves a
        // hole: the scripted axe hit restarts the attack cooldown, and once it
        // expires with the button still held, doAttack() is called again while the
        // combo is mid-sequence. Falling through there would fire an unscoped
        // vanilla hit with whatever item the combo has just selected, breaking the
        // scripted order that acceptance test 4 depends on.
        if (ComboController.isRunning()) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
