package com.nyamtils.features.qol;

import com.mojang.authlib.GameProfile;
import com.nyamtils.NyamTils;
import com.nyamtils.features.Feature;
import com.nyamtils.utils.ChatUtils;
import com.nyamtils.utils.HypixelUtils;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Faithful port of the AutoMeow reference mod (org.rhynohowl.automeow).
 * Detects "meow" variants in any Hypixel channel and replies in the same channel.
 * Self-messages are skipped by resolving the sender's name from the chat event.
 */
public class AutoMeowFeature implements Feature {

    private enum Channel { ALL, GUILD, PARTY, COOP, PM, IGNORE }

    private static final Pattern MEOW_PATTERN = Pattern.compile(
        "(^|\\W)(?:meow+|mr+rp+|mr+ow+|mr+aow+|mer+|nya+~*|purr+|bark+|woof+|wr+uff+|grr+)(\\W|$)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern VANILLA_WHISPER = Pattern.compile(
        "^\\s*<?([A-Za-z0-9_]{3,16})>?\\s+whispers\\s+to\\s+you\\s*:", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHANNEL_WORD = Pattern.compile("^\\s*([A-Za-z]+(?:[-\\p{Pd}][A-Za-z]+)?)");
    private static final Pattern IGN_PATTERN  = Pattern.compile("\\b([A-Za-z0-9_]{3,16})\\b");

    private static final long COOLDOWN_MS = 2000;
    private long lastSentAt = 0;
    private String lastWhisperFrom = null;

    @Override
    public void init() {
        // Player chat carries the sender's GameProfile.
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) ->
            handle(message.getString(), sender));
        // Hypixel channel messages arrive as system/game messages (no profile).
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) handle(message.getString(), null);
        });
    }

    @Override
    public String getId() { return "auto_meow"; }

    private void handle(String raw, GameProfile senderProfile) {
        if (!NyamTils.CONFIG.autoMeowEnabled) return;
        if (!HypixelUtils.isOnHypixel()) return;
        if (raw == null) return;

        String clean = raw.replaceAll("§.", "").replaceAll("\\p{Pd}", "-").trim();
        if (clean.isEmpty()) return;
        if (!MEOW_PATTERN.matcher(clean).find()) return;

        Channel channel = detect(clean);
        if (channel == Channel.IGNORE) return;

        // Skip our own messages (covers both what we type and the echo of our replies)
        String ourName = ourName();
        if (senderProfile != null && senderProfile.name() != null
                && senderProfile.name().equalsIgnoreCase(ourName)) return;
        String sender = extractSender(clean);
        if (sender != null && sender.equalsIgnoreCase(ourName)) return;

        if (System.currentTimeMillis() - lastSentAt < COOLDOWN_MS) return;

        String response = NyamTils.CONFIG.getActiveMeowResponse();
        switch (channel) {
            case PARTY -> send("pc " + response);
            case GUILD -> send("gc " + response);
            case COOP  -> send("cc " + response);
            case PM -> {
                String target = pmTarget(clean);
                if (target != null && !target.isBlank()) {
                    lastWhisperFrom = target;
                    send("w " + target + " " + response);
                }
            }
            case ALL -> sendChat(response);
            default -> { }
        }
    }

    /** Classify by the leading channel word (mirrors HpChannel.detect). */
    private Channel detect(String clean) {
        Matcher wm = CHANNEL_WORD.matcher(clean);
        if (wm.find()) {
            String word = wm.group(1).toLowerCase().replaceAll("[^a-z]", "");
            switch (word) {
                case "party": return Channel.PARTY;
                case "guild": return Channel.GUILD;
                case "coop":  return Channel.COOP;
                case "from":  return Channel.PM;
                case "to":    return Channel.IGNORE;
                default: break;
            }
        }
        if (VANILLA_WHISPER.matcher(clean).find()) return Channel.PM;
        return Channel.ALL;
    }

    /** The IGN that sent the message: last name token before the chat colon, ignoring rank/level tags. */
    private String extractSender(String clean) {
        int colon = clean.indexOf(':');
        if (colon < 0) return null;
        String prefix = clean.substring(0, colon).replaceAll("\\[[^\\]]*\\]", " ");
        Matcher m = IGN_PATTERN.matcher(prefix);
        String last = null;
        while (m.find()) last = m.group(1);
        return last;
    }

    private String pmTarget(String clean) {
        Matcher vw = VANILLA_WHISPER.matcher(clean);
        if (vw.find()) return vw.group(1);
        String afterFrom = clean.replaceFirst("(?i)^\\s*from", "").replaceAll("\\[[^\\]]*\\]", " ");
        Matcher ign = IGN_PATTERN.matcher(afterFrom);
        if (ign.find()) return ign.group(1);
        return lastWhisperFrom;
    }

    private void send(String command) {
        lastSentAt = System.currentTimeMillis();
        ChatUtils.sendCommand(command);
    }

    private void sendChat(String message) {
        lastSentAt = System.currentTimeMillis();
        ChatUtils.sendMessage(message);
    }

    private static String ourName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "";
        return mc.player.getGameProfile().name();
    }
}
