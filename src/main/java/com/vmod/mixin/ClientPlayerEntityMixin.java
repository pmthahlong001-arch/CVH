package com.vmod.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Reserved for future player-specific hooks
 * (e.g. bow-release detection for AutoCart BOW mode).
 */
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
    // Hook bow release → AutoCart.onBowRelease() if desired
}
