package com.nyamtils.mixin;

import com.nyamtils.NyamTils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Backs the "Hide Armor" tweak by blanking the render state's equipment fields right after
 * they're populated, so {@code HumanoidArmorLayer} sees empty stacks and skips drawing them.
 */
@Mixin(AvatarRenderer.class)
public class AvatarArmorHideMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL")
    )
    private void nyamtils$hideArmor(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        boolean self = entity instanceof LocalPlayer;
        boolean hide = NyamTils.CONFIG.hideArmorEnabled && (self || NyamTils.CONFIG.hideArmorTeammates);
        if (!hide) return;

        state.headEquipment = ItemStack.EMPTY;
        if (!NyamTils.CONFIG.hideArmorOnlyHelmet) {
            state.chestEquipment = ItemStack.EMPTY;
            state.legsEquipment = ItemStack.EMPTY;
            state.feetEquipment = ItemStack.EMPTY;
        }
    }
}
