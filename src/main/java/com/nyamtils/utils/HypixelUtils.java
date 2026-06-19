package com.nyamtils.utils;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworkHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

public class HypixelUtils {

    private static boolean inSkyblock = false;
    private static boolean inDungeons = false;

    public static boolean isOnHypixel() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getCurrentServerEntry() == null) return false;
        String address = client.getCurrentServerEntry().address.toLowerCase();
        return address.contains("hypixel.net");
    }

    public static boolean isInSkyblock() {
        return isOnHypixel() && inSkyblock;
    }

    public static boolean isInDungeons() {
        return isInSkyblock() && inDungeons;
    }

    public static void setInSkyblock(boolean value) {
        inSkyblock = value;
        if (!value) inDungeons = false;
    }

    public static void setInDungeons(boolean value) {
        inDungeons = value;
    }
}
