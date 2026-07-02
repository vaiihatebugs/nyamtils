package com.nyamtils.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public class SoundUtils {

    /** Plays a sound by id (e.g. "block.note_block.pling" or "namespace:path"). No-op if invalid. */
    public static void play(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || id == null || id.isBlank()) return;

        Identifier rl;
        try {
            int sep = id.indexOf(':');
            rl = sep >= 0
                ? Identifier.fromNamespaceAndPath(id.substring(0, sep), id.substring(sep + 1))
                : Identifier.fromNamespaceAndPath("minecraft", id);
        } catch (Exception e) {
            return;
        }
        BuiltInRegistries.SOUND_EVENT.getOptional(rl)
            .ifPresent(sound -> mc.player.playSound(sound, 100.0f, 1.0f));
    }
}
