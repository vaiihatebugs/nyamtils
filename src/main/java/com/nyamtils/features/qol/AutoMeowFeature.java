package com.nyamtils.features.qol;

import com.nyamtils.NyamTils;
import com.nyamtils.events.ChatListener;
import com.nyamtils.features.Feature;
import com.nyamtils.utils.ChatUtils;
import com.nyamtils.utils.HypixelUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoMeowFeature implements Feature {

    // Matches party, guild, all-chat, and private messages that contain exactly "meow"
    // Group 1 = channel prefix ("/p ", "/gc ", "" for all, "/msg <name> " for DM)
    private static final Pattern PARTY_MSG   = Pattern.compile("^Party > .+: meow$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GUILD_MSG   = Pattern.compile("^Guild > .+: meow$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALL_MSG     = Pattern.compile("^[^:]+: meow$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DM_FROM     = Pattern.compile("^From (.+): meow$", Pattern.CASE_INSENSITIVE);

    private static final long COOLDOWN_MS = 2000;
    private long lastSentAt = 0;

    @Override
    public void init() {
        ChatListener.register(this::onChat);
    }

    @Override
    public String getId() {
        return "auto_meow";
    }

    private void onChat(String message) {
        if (!NyamTils.CONFIG.autoMeowEnabled) return;
        if (!HypixelUtils.isOnHypixel()) return;
        if (System.currentTimeMillis() - lastSentAt < COOLDOWN_MS) return;

        String response = null;

        if (PARTY_MSG.matcher(message).matches()) {
            response = "/pc meow";
        } else if (GUILD_MSG.matcher(message).matches()) {
            response = "/gc meow";
        } else {
            Matcher dm = DM_FROM.matcher(message);
            if (dm.matches()) {
                String sender = dm.group(1).trim();
                response = "/msg " + sender + " meow";
            } else if (ALL_MSG.matcher(message).matches()) {
                response = "meow";
            }
        }

        if (response != null) {
            lastSentAt = System.currentTimeMillis();
            final String cmd = response;
            if (cmd.startsWith("/")) {
                // Strip the leading slash — sendCommand takes the command without it
                ChatUtils.sendCommand(cmd.substring(1));
            } else {
                ChatUtils.sendMessage(cmd);
            }
        }
    }
}
