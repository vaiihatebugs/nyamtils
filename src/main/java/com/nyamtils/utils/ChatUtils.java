package com.nyamtils.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ChatUtils {

    public static void sendMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        client.player.networkHandler.sendChatMessage(message);
    }

    public static void sendCommand(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        client.player.networkHandler.sendCommand(command);
    }

    public static void printToChat(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        client.player.sendMessage(Text.literal(message), false);
    }
}
