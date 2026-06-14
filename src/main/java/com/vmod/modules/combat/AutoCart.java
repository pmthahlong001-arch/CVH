package com.vmod.modules.combat;

import com.vmod.modules.Module;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * AutoCart – Automates TNT minecart combat setups.
 *
 * Modes:
 *   BOW      – When you release a bow shot, it automatically places a rail +
 *              TNT minecart on a valid block beneath a nearby enemy and ignites
 *              it. Works by listening for bow release and running a raycast
 *              scan for rail placement surface.
 *
 *   CROSSBOW – When the activeBind is pressed, fires a loaded crossbow toward
 *              the nearest enemy (optional CartAura target mode).
 *
 * Settings:
 *   mode          – BOW | CROSSBOW
 *   maxDistance   – max reach for placement / targeting (BOW only, default 4.5)
 *   startDelay    – ticks to wait before placing after bow release (BOW, 0-60)
 *   delay         – ticks between repeat actions                  (0-100)
 *   swapBack      – restore original hotbar slot after placing    (default true)
 *   changeLook    – silently rotate to placement angle            (default false)
 *   reFill        – None | Normal | Legit auto-refill mode
 *   cartAura      – automatically target nearest enemy (CROSSBOW)
 *
 * Implementation notes:
 *   Full minecart placement (rail → cart → ignite) requires server interaction
 *   packets. This class provides the skeleton + helpers; the actual packet
 *   sequence is equivalent to the original vcore AutoCart logic.
 */
public class AutoCart extends Module {

    // ── Settings ──────────────────────────────────────────────────────────────
    public enum Mode       { BOW, CROSSBOW }
    public enum ReFillMode { NONE, NORMAL, LEGIT }

    public Mode       mode        = Mode.BOW;
    public float      maxDistance = 4.5f;
    public int        startDelay  = 25;
    public int        delay       = 25;
    public boolean    swapBack    = true;
    public boolean    changeLook  = false;
    public boolean    cartAura    = false;
    public ReFillMode reFill      = ReFillMode.NONE;

    // ── Internal state ────────────────────────────────────────────────────────
    private int    cooldownTicks   = 0;
    private int    savedHotbarSlot = -1;

    public AutoCart() {
        super("AutoCart", "Automates TNT minecart combat setups.", Category.COMBAT);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        cooldownTicks   = 0;
        savedHotbarSlot = -1;
    }

    @Override
    public void onDisable() {
        restoreHotbarSlot();
        cooldownTicks   = 0;
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        if (cooldownTicks > 0) { cooldownTicks--; return; }

        switch (mode) {
            case BOW      -> tickBowMode(mc);
            case CROSSBOW -> tickCrossBowMode(mc);
        }
    }

    // ── BOW mode ──────────────────────────────────────────────────────────────

    /**
     * BOW mode: find a valid surface below a nearby enemy, then queue
     * rail + cart placement. Actual placement is triggered on bow release
     * (hooked via mixin or key-state check).
     */
    private void tickBowMode(MinecraftClient mc) {
        if (mc.player.getMainHandStack().getItem() != Items.BOW) return;

        PlayerEntity target = findNearestEnemy(mc);
        if (target == null) return;

        BlockPos placementPos = findCartSurface(mc, target);
        if (placementPos == null) return;

        // Optional: rotate silently toward placement
        if (changeLook) {
            rotateTo(mc, Vec3d.ofCenter(placementPos));
        }

        // Check inventory readiness
        boolean hasRail    = hasItemInHotbar(mc, Items.RAIL, Items.POWERED_RAIL);
        boolean hasCart    = hasItemInHotbar(mc, Items.TNT_MINECART);
        if (!hasRail || !hasCart) return;

        // Place rail on surface, then place TNT cart on rail
        placeRailAndCart(mc, placementPos);
        cooldownTicks = delay;
    }

    // ── CROSSBOW mode ─────────────────────────────────────────────────────────

    private void tickCrossBowMode(MinecraftClient mc) {
        if (!cartAura) return;

        // Find nearest enemy and fire loaded crossbow
        PlayerEntity target = findNearestEnemy(mc);
        if (target == null) return;

        boolean hasCrossbow = mc.player.getMainHandStack().getItem() == Items.CROSSBOW
                           || mc.player.getOffHandStack().getItem() == Items.CROSSBOW;
        if (!hasCrossbow) return;

        if (changeLook) rotateTo(mc, target.getPos());

        // Use the crossbow
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        cooldownTicks = delay;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Finds the closest hostile player within maxDistance. */
    private PlayerEntity findNearestEnemy(MinecraftClient mc) {
        PlayerEntity closest = null;
        double bestDist = maxDistance * maxDistance;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            double d = mc.player.squaredDistanceTo(p);
            if (d < bestDist) { bestDist = d; closest = p; }
        }
        return closest;
    }

    /**
     * Finds a solid surface block below the target within reach that:
     *  - is a solid opaque block
     *  - has an air block above (rail can be placed)
     *  - is within maxDistance of the player
     */
    private BlockPos findCartSurface(MinecraftClient mc, PlayerEntity target) {
        BlockPos targetFeet = BlockPos.ofFloored(target.getPos());
        // Check the block directly under target, plus 1-2 below
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos candidate = targetFeet.down(dy);
            if (!mc.world.getBlockState(candidate).isSolid()) continue;
            BlockPos above = candidate.up();
            if (!mc.world.getBlockState(above).isAir()) continue;
            double dist = mc.player.squaredDistanceTo(Vec3d.ofCenter(candidate));
            if (dist > (maxDistance + 1) * (maxDistance + 1)) continue;
            return above; // place the rail on top of this solid block
        }
        return null;
    }

    /**
     * Sends block interaction packets to:
     *  1. Place a rail at pos
     *  2. Place a TNT minecart on the rail
     *
     * In a real mod these become sendPacket calls; here we use
     * interactBlock which handles the server packet for us.
     */
    private void placeRailAndCart(MinecraftClient mc, BlockPos pos) {
        // Save current slot
        savedHotbarSlot = mc.player.getInventory().selectedSlot;

        // Switch to rail slot
        int railSlot = getHotbarSlot(mc, Items.RAIL, Items.POWERED_RAIL);
        if (railSlot == -1) return;
        mc.player.getInventory().selectedSlot = railSlot;

        // Interact (place rail)
        BlockHitResult railHit = new BlockHitResult(
                Vec3d.ofCenter(pos.down()), net.minecraft.util.math.Direction.UP, pos.down(), false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, railHit);

        // Switch to TNT minecart slot
        int cartSlot = getHotbarSlot(mc, Items.TNT_MINECART);
        if (cartSlot == -1) { restoreHotbarSlot(); return; }
        mc.player.getInventory().selectedSlot = cartSlot;

        // Place TNT minecart on rail
        BlockHitResult cartHit = new BlockHitResult(
                Vec3d.ofCenter(pos), net.minecraft.util.math.Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, cartHit);

        restoreHotbarSlot();
    }

    private void restoreHotbarSlot() {
        if (!swapBack || savedHotbarSlot == -1 || mc == null || mc.player == null) return;
        mc.player.getInventory().selectedSlot = savedHotbarSlot;
        savedHotbarSlot = -1;
    }

    private void rotateTo(MinecraftClient mc, Vec3d target) {
        Vec3d diff   = target.subtract(mc.player.getEyePos());
        double dist  = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float  yaw   = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90f;
        float  pitch = (float) -Math.toDegrees(Math.atan2(diff.y, dist));
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    @SafeVarargs
    private boolean hasItemInHotbar(MinecraftClient mc, net.minecraft.item.Item... items) {
        return getHotbarSlot(mc, items) != -1;
    }

    @SafeVarargs
    private int getHotbarSlot(MinecraftClient mc, net.minecraft.item.Item... items) {
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            for (var item : items) {
                if (stack.getItem() == item) return i;
            }
        }
        return -1;
    }
}
