package com.vmod.mixin;

import com.vmod.modules.combat.HitBox;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts Entity#getBoundingBox() to apply HitBox expansion.
 * This affects both client-side ray-casts (clicking) and Aura targeting.
 */
@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "getBoundingBox", at = @At("RETURN"), cancellable = true)
    private void vmod$expandHitBox(CallbackInfoReturnable<Box> cir) {
        Box original = cir.getReturnValue();
        Box expanded = HitBox.getExpandedBox((Object) this, original);
        if (expanded != original) {
            cir.setReturnValue(expanded);
        }
    }
}
