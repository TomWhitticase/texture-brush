package io.github.tomwhitticase.texturebrush.mixin;

import io.github.tomwhitticase.texturebrush.BrushController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    // Runs before vanilla reads the held item, so the swapped block is the one that gets placed.
    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void texturebrush$beforeUse(CallbackInfo ci) {
        BrushController.beforeUse((Minecraft) (Object) this);
    }
}
