package com.vmod;

import com.vmod.gui.ClickGui;
import com.vmod.modules.combat.AutoCart;
import com.vmod.modules.combat.AutoTotem;
import com.vmod.modules.combat.HitBox;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * VMod - Combat utility mod for Minecraft 1.21 Fabric
 *
 * Modules:
 *   - AutoTotem : auto-swaps totem to offhand based on health / threat
 *   - HitBox    : expands entity hitboxes (XZ + Y) with smooth 3-D outline render
 *   - AutoCart  : automates TNT minecart combat (Bow / CrossBow modes)
 *
 * Press RIGHT SHIFT to open the ClickGUI.
 */
public class VMod implements ClientModInitializer {

    public static final String MOD_ID = "vmod";

    // ── Modules ──────────────────────────────────────────────────────────────
    public static AutoTotem autoTotem;
    public static HitBox    hitBox;
    public static AutoCart  autoCart;

    // ── GUI ──────────────────────────────────────────────────────────────────
    public static ClickGui clickGui;

    // ── Keybind: RShift opens menu ────────────────────────────────────────────
    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        // Register modules
        autoTotem = new AutoTotem();
        hitBox    = new HitBox();
        autoCart  = new AutoCart();

        // Register GUI
        clickGui = new ClickGui();

        // Register RShift keybind
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vmod.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.vmod"
        ));

        // Tick listener – check keybind + tick modules
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Open GUI on RShift press
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(clickGui);
                }
            }

            // Tick each module if in-game
            if (client.player == null || client.world == null) return;
            if (autoTotem.isEnabled()) autoTotem.onTick(client);
            if (hitBox.isEnabled())    hitBox.onTick(client);
            if (autoCart.isEnabled())  autoCart.onTick(client);
        });
    }
}
