package com.nyamtils.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ChatUtils {

    public static void sendMessage(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        client.player.connection.sendChat(message);
    }

    public static void sendCommand(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        client.player.connection.sendCommand(command);
    }

    public static void printToChat(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        client.player.sendSystemMessage(Component.literal(message));
    }
}
