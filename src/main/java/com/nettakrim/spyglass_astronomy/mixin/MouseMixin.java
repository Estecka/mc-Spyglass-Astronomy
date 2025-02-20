package com.nettakrim.spyglass_astronomy.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.nettakrim.spyglass_astronomy.SpyglassAstronomyClient;

import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.math.MathHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Mouse.class)
public class MouseMixin {
    @Unique
    private double sensitivityScale;

    @Inject(at = @At("TAIL"), method = "updateMouse")
    public void updateMouse(CallbackInfo ci) {
        if (SpyglassAstronomyClient.isDrawingConstellation) {
            SpyglassAstronomyClient.updateDrawingConstellation();
        }
    }
    @WrapWithCondition(
        method = "onMouseScroll",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerInventory;scrollInHotbar(D)V"
        )
    )
    private boolean onMouseScroll(PlayerInventory inventory, double scroll){
        ClientPlayerEntity player = SpyglassAstronomyClient.client.player;
        if(player != null && player.isUsingSpyglass()){
            SpyglassAstronomyClient.zoom = MathHelper.clamp(SpyglassAstronomyClient.zoom - (float)scroll, -10, 10);
            return false;
        }
        return true;
    }

    @ModifyVariable(
        method = "updateMouse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/tutorial/TutorialManager;onUpdateMouse(DD)V"
        ),
        ordinal = 1
    )
    private double changeXSensitivity(double d) {
        ClientPlayerEntity player = SpyglassAstronomyClient.client.player;
        double angleScale;
        if (player != null && player.isUsingSpyglass() && SpyglassAstronomyClient.client.options.getPerspective().isFirstPerson()) {
            sensitivityScale = (float)Math.pow(1.25d, SpyglassAstronomyClient.zoom);
            float cosAngle = (MathHelper.cos(player.getPitch()/180*MathHelper.PI));
            if (cosAngle < 0) cosAngle *= -1;
            cosAngle = Math.max(cosAngle, (Math.max(SpyglassAstronomyClient.zoom,0)+1)/11);
            angleScale = 1/cosAngle;
        } else {
            sensitivityScale = 1;
            angleScale = 1;
        }
        return d * sensitivityScale * angleScale;
    }

    @ModifyVariable(
        method = "updateMouse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/tutorial/TutorialManager;onUpdateMouse(DD)V"
        ),
        ordinal = 2
    )
    private double changeYSensitivity(double d) {
        return d * sensitivityScale;
    }
}
