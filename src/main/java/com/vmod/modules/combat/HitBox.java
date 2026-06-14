package com.vmod.modules.combat;

import com.vmod.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.util.math.Box;

/**
 * HitBox – Expands entity hitboxes for easier melee.
 *
 * Settings:
 *   xzExpand    – horizontal expansion in blocks (each side), default 0.7
 *   yExpand     – vertical expansion in blocks   (each side), default 0.0
 *   onlyWeapon  – only expand when holding sword / axe / mace
 *   renderBox   – draw a white wireframe around expanded boxes (via mixin)
 *
 * Integration:
 *   EntityMixin calls HitBox.getExpandedBox(entity, original) to return the
 *   modified bounding box for ray-cast / hit detection (affects Aura too).
 *
 *   The 3-D wireframe render is handled by a separate render event that loops
 *   world players and draws expanded boxes using GL lines.
 *
 * Note: actual mixin wiring lives in EntityMixin.java.
 */
public class HitBox extends Module {

    // ── Settings ──────────────────────────────────────────────────────────────
    public float   xzExpand   = 0.7f;
    public float   yExpand    = 0.0f;
    public boolean onlyWeapon = false;
    public boolean renderBox  = true;

    // Singleton reference so the mixin can access settings without DI
    public static HitBox INSTANCE;

    public HitBox() {
        super("HitBoxes", "Increases entity hitboxes.", Category.COMBAT);
        INSTANCE = this;
    }

    // ── Public API (called by EntityMixin) ────────────────────────────────────

    /**
     * Returns an expanded Box if expansion should apply to this entity,
     * or the original box otherwise.
     */
    public static Box getExpandedBox(Object entity, Box original) {
        HitBox inst = INSTANCE;
        if (inst == null || !inst.isEnabled()) return original;
        if (!(entity instanceof PlayerEntity player))  return original;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return original;
        if (player.getUuid().equals(mc.player.getUuid())) return original; // skip self

        if (inst.onlyWeapon && !inst.holdingWeapon(mc)) return original;

        double halfXZ = inst.xzExpand / 2.0;
        double halfY  = inst.yExpand  / 2.0;
        return new Box(
                original.minX - halfXZ, original.minY - halfY, original.minZ - halfXZ,
                original.maxX + halfXZ, original.maxY + halfY, original.maxZ + halfXZ
        );
    }

    private boolean holdingWeapon(MinecraftClient mc) {
        if (mc.player == null) return false;
        var item = mc.player.getMainHandStack().getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof MaceItem;
    }

    // ── Tick (optional per-tick logic) ────────────────────────────────────────

    @Override
    public void onTick(MinecraftClient mc) {
        // Render is handled in the mixin / render hook; nothing needed here.
    }

    @Override
    public void onDisable() {
        // Box reverts automatically because the mixin checks isEnabled().
    }
}
