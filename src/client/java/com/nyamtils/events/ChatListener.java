package com.nyamtils.events;

import com.nyamtils.utils.HypixelUtils;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ChatListener {

    private static final List<Consumer<String>> LISTENERS = new ArrayList<>();

    public static void init() {
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (!HypixelUtils.isOnHypixel()) return;
            String plain = message.getString();
            for (Consumer<String> listener : LISTENERS) {
                listener.accept(plain);
            }
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!HypixelUtils.isOnHypixel() || overlay) return;
            String plain = message.getString();
            for (Consumer<String> listener : LISTENERS) {
                listener.accept(plain);
            }
        });
    }

    public static void register(Consumer<String> listener) {
        LISTENERS.add(listener);
    }
}
