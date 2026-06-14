package com.vmod.gui;

import com.vmod.VMod;
import com.vmod.modules.Module;
import com.vmod.modules.combat.AutoCart;
import com.vmod.modules.combat.AutoTotem;
import com.vmod.modules.combat.HitBox;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * ClickGUI – Draggable panel listing all VMod modules.
 *
 * Layout:
 *   ┌──────────────────────────┐
 *   │  VMod  [drag here]       │  ← title bar (drag to move)
 *   ├──────────────────────────┤
 *   │  ○ AutoTotem    [SMART▾] │  ← module row; click name to toggle
 *   │  ○ HitBoxes              │
 *   │  ○ AutoCart     [BOW▾]   │
 *   ├──────────────────────────┤
 *   │  Settings panel          │  ← appears when a module is selected
 *   └──────────────────────────┘
 *
 * Colours:
 *   Background  #1a1a2e  (dark navy)
 *   Title bar   #16213e
 *   Enabled     #e94560  (red accent)
 *   Disabled    #4a4a6a
 *   Text        #eaeaea
 *   Selected    #0f3460  (blue)
 */
public class ClickGui extends Screen {

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final int BG         = 0xee1a1a2e;
    private static final int TITLEBAR   = 0xff16213e;
    private static final int ENABLED    = 0xffe94560;
    private static final int DISABLED   = 0xff4a4a6a;
    private static final int TEXT_COLOR = 0xffeaeaea;
    private static final int SELECTED   = 0xff0f3460;
    private static final int HOVER      = 0xff222244;

    // ── Dimensions ────────────────────────────────────────────────────────────
    private static final int PANEL_W     = 200;
    private static final int TITLE_H     = 16;
    private static final int ROW_H       = 18;
    private static final int SETTINGS_H  = 90;
    private static final int PADDING     = 6;

    // ── State ─────────────────────────────────────────────────────────────────
    private int panelX = 20;
    private int panelY = 20;
    private boolean dragging    = false;
    private int     dragOffsetX = 0;
    private int     dragOffsetY = 0;

    private Module selectedModule = null;

    /** All modules in display order. */
    private final List<Module> modules = new ArrayList<>();

    public ClickGui() {
        super(Text.literal("VMod"));
    }

    @Override
    protected void init() {
        modules.clear();
        modules.add(VMod.autoTotem);
        modules.add(VMod.hitBox);
        modules.add(VMod.autoCart);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Dim background slightly
        ctx.fill(0, 0, width, height, 0x88000000);

        int panelH = TITLE_H + modules.size() * ROW_H
                + (selectedModule != null ? SETTINGS_H + 4 : 0)
                + 4;

        // Panel shadow
        ctx.fill(panelX + 3, panelY + 3, panelX + PANEL_W + 3, panelY + panelH + 3, 0x66000000);

        // Panel background
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, BG);

        // Title bar
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + TITLE_H, TITLEBAR);
        ctx.drawText(textRenderer, "§b§lVMod", panelX + PADDING, panelY + 4, TEXT_COLOR, false);

        // Module rows
        int rowY = panelY + TITLE_H + 2;
        for (Module mod : modules) {
            boolean hovered  = isInside(mouseX, mouseY, panelX, rowY, PANEL_W, ROW_H);
            boolean selected = mod == selectedModule;

            int rowBg = selected ? SELECTED : hovered ? HOVER : 0x00000000;
            if (rowBg != 0) ctx.fill(panelX, rowY, panelX + PANEL_W, rowY + ROW_H, rowBg);

            // Enabled indicator dot
            int dotColor = mod.isEnabled() ? ENABLED : DISABLED;
            ctx.fill(panelX + PADDING - 1, rowY + 6, panelX + PADDING + 5, rowY + 12, dotColor);

            // Module name
            String label = mod.getName();
            ctx.drawText(textRenderer, label, panelX + PADDING + 8, rowY + 5, TEXT_COLOR, false);

            // Mode suffix (for modules that have a mode setting)
            String modeSuffix = getModeSuffix(mod);
            if (!modeSuffix.isEmpty()) {
                int suffixX = panelX + PANEL_W - textRenderer.getWidth(modeSuffix) - PADDING;
                ctx.drawText(textRenderer, "§7" + modeSuffix, suffixX, rowY + 5, TEXT_COLOR, false);
            }

            rowY += ROW_H;
        }

        // Settings sub-panel
        if (selectedModule != null) {
            rowY += 2;
            ctx.fill(panelX, rowY, panelX + PANEL_W, rowY + SETTINGS_H, 0xcc101025);
            renderSettings(ctx, selectedModule, panelX + PADDING, rowY + PADDING, mouseX, mouseY);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    /**
     * Renders inline settings for the selected module.
     * Each setting is shown as a labelled toggle or value row.
     */
    private void renderSettings(DrawContext ctx, Module mod, int x, int y, int mouseX, int mouseY) {
        ctx.drawText(textRenderer, "§e" + mod.getName() + " Settings", x, y, TEXT_COLOR, false);
        y += 12;

        if (mod instanceof AutoTotem at) {
            y = drawToggle(ctx, x, y, "Mode: " + at.mode.name(), true, mouseX, mouseY);
            y = drawToggle(ctx, x, y, "On Fall",     at.onFall,     mouseX, mouseY);
            y = drawToggle(ctx, x, y, "On Elytra",   at.onElytra,   mouseX, mouseY);
            y = drawToggle(ctx, x, y, "On TNT",      at.onTNT,      mouseX, mouseY);
            y = drawToggle(ctx, x, y, "On Creeper",  at.onCreeper,  mouseX, mouseY);
            drawValue (ctx, x, y, "HP: " + at.healthHP);
        } else if (mod instanceof HitBox hb) {
            y = drawToggle(ctx, x, y, "Only Weapon", hb.onlyWeapon, mouseX, mouseY);
            y = drawToggle(ctx, x, y, "Render Box",  hb.renderBox,  mouseX, mouseY);
            y = drawValue (ctx, x, y, "XZ: " + hb.xzExpand);
            drawValue(ctx, x, y + 10, "Y:  " + hb.yExpand);
        } else if (mod instanceof AutoCart ac) {
            y = drawToggle(ctx, x, y, "Mode: " + ac.mode.name(), true, mouseX, mouseY);
            y = drawToggle(ctx, x, y, "Swap Back",   ac.swapBack,   mouseX, mouseY);
            y = drawToggle(ctx, x, y, "Change Look", ac.changeLook, mouseX, mouseY);
            drawValue (ctx, x, y, "Dist: " + ac.maxDistance);
        }
    }

    private int drawToggle(DrawContext ctx, int x, int y, String label, boolean value, int mx, int my) {
        String indicator = value ? "§a✔" : "§c✘";
        ctx.drawText(textRenderer, indicator + " §7" + label, x, y, TEXT_COLOR, false);
        return y + 10;
    }

    private int drawValue(DrawContext ctx, int x, int y, String label) {
        ctx.drawText(textRenderer, "§9■ §7" + label, x, y, TEXT_COLOR, false);
        return y + 10;
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int imx = (int) mx, imy = (int) my;

        // Title bar drag
        if (button == 0 && isInside(imx, imy, panelX, panelY, PANEL_W, TITLE_H)) {
            dragging    = true;
            dragOffsetX = imx - panelX;
            dragOffsetY = imy - panelY;
            return true;
        }

        // Module rows
        int rowY = panelY + TITLE_H + 2;
        for (Module mod : modules) {
            if (isInside(imx, imy, panelX, rowY, PANEL_W, ROW_H)) {
                if (button == 0) {
                    mod.toggle();            // left-click → toggle on/off
                } else if (button == 1) {
                    // right-click → open / close settings
                    selectedModule = (selectedModule == mod) ? null : mod;
                }
                return true;
            }
            rowY += ROW_H;
        }

        // Settings interactions
        if (selectedModule != null) {
            handleSettingsClick(imx, imy, rowY + 2 + PADDING + 12, button);
        }

        return super.mouseClicked(mx, my, button);
    }

    /** Cycles enum or toggles boolean settings when clicked. */
    private void handleSettingsClick(int mx, int my, int startY, int button) {
        if (button != 0) return;
        int lineH = 10;
        int x = panelX + PADDING;

        if (selectedModule instanceof AutoTotem at) {
            if (isInside(mx, my, x, startY, PANEL_W - PADDING * 2, lineH))
                at.mode = (at.mode == AutoTotem.Mode.TOTEM) ? AutoTotem.Mode.SMART : AutoTotem.Mode.TOTEM;
            startY += lineH;
            if (isInside(mx, my, x, startY, PANEL_W - PADDING * 2, lineH)) at.onFall    = !at.onFall;    startY += lineH;
            if (isInside(mx, my, x, startY, PANEL_W - PADDING * 2, lineH)) at.onElytra  = !at.onElytra;  startY += lineH;
            if (isInside(mx, my, x, startY, PANEL_W - PADDING * 2, lineH)) at.onTNT     = !at.onTNT;     startY += lineH;
            if (isInside(mx, my, x, startY, PANEL_W - PADDING * 2, lineH)) at.onCreeper = !at.onCreeper;

        } else if (selectedModule instanceof HitBox hb) {
            if (isInside(mx, my, x, startY, PANEL_W - PADDING * 2, lineH)) hb.onlyWeapon = !hb.onlyWeapon; startY += lineH;
            if (isInside(mx, my, x, startY, PANEL_W - PADDING * 2, lineH)) hb.renderBox  = !hb.renderBox;

        } else if (selectedModule instanceof AutoCart ac) {
            if (isInside(mx, my, x, startY, PANEL_W - PADDING * 2, lineH))
                ac.mode = (ac.mode == AutoCart.Mode.BOW) ? AutoCart.Mode.CROSSBOW : AutoCart.Mode.BOW;
            startY += lineH;
            if (isInside(mx, my, x, startY, PANEL_W - PADDING * 2, lineH)) ac.swapBack   = !ac.swapBack;   startY += lineH;
            if (isInside(mx, my, x, startY, PANEL_W - PADDING * 2, lineH)) ac.changeLook = !ac.changeLook;
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) {
            panelX = (int) mx - dragOffsetX;
            panelY = (int) my - dragOffsetY;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Override
    public boolean shouldPause() { return false; } // keep game running

    private boolean isInside(int px, int py, int rx, int ry, int rw, int rh) {
        return px >= rx && px < rx + rw && py >= ry && py < ry + rh;
    }

    private String getModeSuffix(Module mod) {
        if (mod instanceof AutoTotem at) return at.mode.name();
        if (mod instanceof AutoCart  ac) return ac.mode.name();
        return "";
    }
}
