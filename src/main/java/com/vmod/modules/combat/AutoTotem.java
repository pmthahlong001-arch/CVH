package com.vmod.modules.combat;

import com.vmod.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * AutoTotem – Keeps a totem of undying in the offhand.
 *
 * Settings (all configurable via ClickGUI):
 *   mode        – Totem (always) | Smart (health-based)
 *   healthHP    – switch to totem below this HP            (Smart only)
 *   elytraHP    – switch threshold while wearing elytra    (Smart only)
 *   onFall      – force totem during dangerous fall        (Smart only)
 *   onElytra    – force totem while flying elytra          (Smart only)
 *   onTNT       – force totem when TNT is nearby           (Smart only)
 *   onCreeper   – force totem when a creeper is nearby     (Smart only)
 *   onMinecart  – force totem when TNT minecart is nearby  (Smart only)
 *   delayTicks  – ticks to wait before swapping (anti-grim, 1-5)
 *
 * Logic mirrors the original vcore AutoTotem:
 *   1. Determine which item SHOULD be in offhand.
 *   2. If offhand already has it → do nothing.
 *   3. Otherwise search inventory slots 0-35, take the first match
 *      and send a SWAP packet (key slot 40 = offhand swap).
 */
public class AutoTotem extends Module {

    // ── Settings ──────────────────────────────────────────────────────────────
    public enum Mode { TOTEM, SMART }

    public Mode    mode       = Mode.TOTEM;
    public float   healthHP   = 3.5f;   // swap threshold (normal)
    public float   elytraHP   = 16.0f;  // swap threshold (elytra)
    public boolean onFall     = false;
    public boolean onElytra   = false;
    public boolean onTNT      = false;
    public boolean onCreeper  = false;
    public boolean onMinecart = false;
    public int     delayTicks = 2;

    // ── Internal state ────────────────────────────────────────────────────────
    private int ticksUntilSwap = 0;
    private int pendingSlot    = -1;

    public AutoTotem() {
        super("AutoTotem", "Auto-places totems (or chosen item) in offhand.", Category.COMBAT);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        int targetSlot = resolveTargetSlot(mc);

        if (targetSlot == -1) {
            pendingSlot    = -1;
            ticksUntilSwap = 0;
            return;
        }

        // Delay swap logic (anti-Grim style)
        if (targetSlot != pendingSlot) {
            pendingSlot    = targetSlot;
            ticksUntilSwap = delayTicks;
        }

        if (ticksUntilSwap > 0) {
            ticksUntilSwap--;
            return;
        }

        performSwap(mc, targetSlot);
        pendingSlot    = -1;
        ticksUntilSwap = 0;
    }

    // ── Core logic ────────────────────────────────────────────────────────────

    /**
     * Decides which inventory slot to swap to offhand.
     * Returns -1 if no swap is needed.
     */
    private int resolveTargetSlot(MinecraftClient mc) {
        var offhandStack = mc.player.getOffHandStack();
        boolean offhandHasTotem = offhandStack.getItem() == Items.TOTEM_OF_UNDYING;

        boolean needTotem = switch (mode) {
            case TOTEM -> !offhandHasTotem;
            case SMART -> smartNeedsTotem(mc, offhandHasTotem);
        };

        if (!needTotem) return -1;
        if (offhandHasTotem) return -1; // already in place

        return findTotemInInventory(mc);
    }

    private boolean smartNeedsTotem(MinecraftClient mc, boolean offhandHasTotem) {
        float hp = mc.player.getHealth();
        float threshold = isWearingElytra(mc) ? elytraHP : healthHP;

        if (hp <= threshold) return true;

        // Safety checks
        if (onFall && isFallingDangerously(mc)) return true;
        if (onElytra && mc.player.isFallFlying())  return true;

        if (onTNT || onCreeper || onMinecart) {
            for (Entity e : mc.world.getEntities()) {
                if (e == null || !e.isAlive()) continue;
                double dist = mc.player.squaredDistanceTo(e);
                if (dist > 36.0) continue; // 6-block radius
                if (onTNT      && e instanceof TntEntity)          return true;
                if (onMinecart && e instanceof TntMinecartEntity)   return true;
                if (onCreeper  && e instanceof CreeperEntity)       return true;
            }
        }

        return false;
    }

    private boolean isWearingElytra(MinecraftClient mc) {
        var chestStack = mc.player.getInventory().getArmorStack(2);
        return chestStack.getItem() == Items.ELYTRA;
    }

    private boolean isFallingDangerously(MinecraftClient mc) {
        // Rough heuristic: falling fast and will take lethal fall damage
        double fallDist   = mc.player.fallDistance;
        float  dmgExpected = (float)(fallDist - 3.0) / 2.0f + 3.5f;
        return mc.player.getVelocity().y < -0.5 && (mc.player.getHealth() - dmgExpected) < 0.5f;
    }

    /**
     * Scans inventory slots 0-35 for a totem of undying.
     * Returns the slot index, or -1 if none found.
     */
    private int findTotemInInventory(MinecraftClient mc) {
        var inventory = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inventory.getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Sends the SWAP packet that moves a hotbar/inventory slot to the offhand.
     * Slot index 40 in the container = offhand; key 40 maps to F key (swap with offhand).
     */
    private void performSwap(MinecraftClient mc, int invSlot) {
        // Convert raw inventory slot to container slot id
        int containerSlot = invSlot < 9 ? 36 + invSlot : invSlot;
        mc.interactionManager.clickSlot(
                mc.player.playerScreenHandler.syncId,
                containerSlot,
                40, // hotkey number for offhand swap
                SlotActionType.SWAP,
                mc.player
        );
    }

    // ── Disable cleanup ───────────────────────────────────────────────────────

    @Override
    public void onDisable() {
        pendingSlot    = -1;
        ticksUntilSwap = 0;
    }
}
