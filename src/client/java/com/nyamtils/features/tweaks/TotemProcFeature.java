package com.nyamtils.features.tweaks;

import com.nyamtils.NyamTils;
import com.nyamtils.events.ChatListener;
import com.nyamtils.features.Feature;
import com.nyamtils.utils.SoundUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.regex.Pattern;

/**
 * Faithful port of SkyOcean's InvulnerabilityAnimation: plays the vanilla Totem of Undying
 * sound (and optionally its screen animation) when Bonzo's Mask, Spirit Mask, or the Phoenix
 * Pet saves your life.
 */
public class TotemProcFeature implements Feature {

    private static final Pattern BONZO_MASK = Pattern.compile("Your (?:⚚ )?Bonzo's Mask saved your life!");
    private static final Pattern PHOENIX_PET = Pattern.compile("Your Phoenix Pet saved you from certain death!");
    private static final Pattern SPIRIT_MASK = Pattern.compile("Second Wind Activated! Your Spirit Mask saved your life!");

    @Override
    public void init() {
        ChatListener.register(this::handle);
    }

    @Override
    public String getId() { return "totem_proc"; }

    private void handle(String raw) {
        if (!NyamTils.CONFIG.totemProcEnabled) return;
        if (raw == null) return;

        String clean = raw.replaceAll("§.", "");
        if (!BONZO_MASK.matcher(clean).matches()
            && !PHOENIX_PET.matcher(clean).matches()
            && !SPIRIT_MASK.matcher(clean).matches()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        SoundUtils.play("minecraft:item.totem.use");

        if (NyamTils.CONFIG.totemProcAnimation) {
            mc.particleEngine.createTrackingEmitter(player, ParticleTypes.TOTEM_OF_UNDYING, 30);
            mc.gameRenderer.displayItemActivation(new ItemStack(Items.TOTEM_OF_UNDYING));
        }
    }
}
