package com.example.pvptestclicker;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.HitResult;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Developer tool for testing PvP mechanics (hit detection, reach, knockback, custom weapons).
 *
 * While enabled, it simulates a press of your Attack key (the left mouse click) whenever ANOTHER
 * PLAYER is within the detection range. It sends no attack commands of its own: the game handles
 * the click exactly as if you had pressed the button, so whatever your crosshair is on is what
 * gets hit. It never turns your camera or picks targets for you.
 *
 * Safeguards, by design:
 *  - Always allowed in singleplayer.
 *  - On multiplayer it only runs on servers listed in "allowedServers" in
 *    config/pvptestclicker.properties (default: localhost only). Anywhere else it refuses to turn on.
 *  - Starts OFF every launch.
 *  - By default it does not click while your crosshair is on a block, so a click can't mine or
 *    break your builds (set clickWhenAimingAtBlocks=true to change that).
 *
 * Only use it where automated clicking is explicitly permitted. Most public servers prohibit it.
 */
public class PvpTestClickerClient implements ClientModInitializer {
    public static final String MOD_ID = "pvptestclicker";

    private static final float MIN_RANGE = 0.5f;
    private static final float MAX_RANGE = 16.0f;
    private static final float RANGE_STEP = 0.5f;
    private static final float MIN_ATTACK_STRENGTH = 0.95f;

    // config (persisted)
    private static float detectionRange = 4.0f;
    private static boolean requireCooldown = true;
    private static boolean clickWhenAimingAtBlocks = false;
    private static final Set<String> allowedServers = new HashSet<>();

    private static boolean enabled = false;
    private static KeyMapping toggleKey, rangeUpKey, rangeDownKey;

    @Override
    public void onInitializeClient() {
        loadConfig();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));

        toggleKey = key("key.pvptestclicker.toggle", InputConstants.KEY_J, category);
        rangeUpKey = key("key.pvptestclicker.range_up", InputConstants.KEY_PERIOD, category);
        rangeDownKey = key("key.pvptestclicker.range_down", InputConstants.KEY_COMMA, category);

        ClientTickEvents.END_CLIENT_TICK.register(PvpTestClickerClient::tick);
    }

    private static KeyMapping key(String translationKey, int code, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(translationKey, code, category));
    }

    private static void say(LocalPlayer player, String text) {
        if (player != null) player.sendSystemMessage(Component.literal("[PvP Test] " + text));
    }

    // ---------------------------------------------------------------- main loop

    private static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;

        while (toggleKey.consumeClick()) {
            if (enabled) {
                enabled = false;
                say(player, "OFF");
            } else if (player != null && mc.level != null) {
                if (isAllowedHere(mc)) {
                    enabled = true;
                    say(player, "ON (range " + detectionRange + " blocks)");
                } else {
                    say(player, "Not enabled: this server is not in allowedServers in "
                            + "config/pvptestclicker.properties. Only use this where automated clicking is permitted.");
                }
            }
        }
        while (rangeUpKey.consumeClick()) {
            detectionRange = Math.min(MAX_RANGE, detectionRange + RANGE_STEP);
            saveConfig();
            say(player, "Detection range: " + detectionRange);
        }
        while (rangeDownKey.consumeClick()) {
            detectionRange = Math.max(MIN_RANGE, detectionRange - RANGE_STEP);
            saveConfig();
            say(player, "Detection range: " + detectionRange);
        }

        if (!enabled) return;
        if (player == null || mc.level == null) {
            enabled = false; // left the world
            return;
        }
        if (!isAllowedHere(mc)) { // switched server while enabled
            enabled = false;
            say(player, "OFF (this server is not allowed)");
            return;
        }

        // Don't act while a menu/chat is open, while dead/spectating, or while eating/blocking.
        if (mc.gui.screen() != null) return;
        if (!player.isAlive() || player.isSpectator() || player.isUsingItem()) return;

        if (!anotherPlayerInRange(mc, player)) return;

        // Don't mine or break blocks by accident.
        if (!clickWhenAimingAtBlocks && mc.hitResult != null
                && mc.hitResult.getType() == HitResult.Type.BLOCK) return;

        if (requireCooldown && player.getAttackStrengthScale(0.5f) < MIN_ATTACK_STRENGTH) return;

        // Just a click: press the Attack key once. The game does the rest, like a real mouse click.
        KeyMapping.click(mc.options.keyAttack.getKey());
    }

    private static boolean anotherPlayerInRange(Minecraft mc, LocalPlayer self) {
        for (AbstractClientPlayer other : mc.level.players()) {
            if (other == self || !other.isAlive() || other.isSpectator()) continue;
            if (self.distanceTo(other) <= detectionRange) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- server allowlist

    private static boolean isAllowedHere(Minecraft mc) {
        if (mc.getSingleplayerServer() != null) return true; // singleplayer / opened-to-LAN own world

        ServerData server = mc.getCurrentServer();
        if (server == null) return false;

        String full = server.ip.trim().toLowerCase(Locale.ROOT);
        return allowedServers.contains(full) || allowedServers.contains(hostOnly(full));
    }

    /** Strips a trailing ":port" from "host:port" (leaves IPv6 and bare hosts alone). */
    private static String hostOnly(String address) {
        int colon = address.lastIndexOf(':');
        if (colon > 0 && address.indexOf(':') == colon) {
            return address.substring(0, colon);
        }
        return address;
    }

    // ---------------------------------------------------------------- config file

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("pvptestclicker.properties");
    }

    private static void loadConfig() {
        Path path = configPath();
        Properties props = new Properties();

        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            } catch (IOException ignored) {
            }
        }

        try {
            detectionRange = Math.max(MIN_RANGE, Math.min(MAX_RANGE,
                    Float.parseFloat(props.getProperty("detectionRange", "4.0"))));
        } catch (NumberFormatException e) {
            detectionRange = 4.0f;
        }
        requireCooldown = Boolean.parseBoolean(props.getProperty("requireCooldown", "true"));
        clickWhenAimingAtBlocks = Boolean.parseBoolean(props.getProperty("clickWhenAimingAtBlocks", "false"));

        allowedServers.clear();
        for (String entry : props.getProperty("allowedServers", "localhost,127.0.0.1").split(",")) {
            String s = entry.trim().toLowerCase(Locale.ROOT);
            if (!s.isEmpty()) allowedServers.add(s);
        }

        if (!Files.exists(path)) saveConfig(); // write defaults so the file is easy to find and edit
    }

    private static void saveConfig() {
        Properties props = new Properties();
        props.setProperty("detectionRange", Float.toString(detectionRange));
        props.setProperty("requireCooldown", Boolean.toString(requireCooldown));
        props.setProperty("clickWhenAimingAtBlocks", Boolean.toString(clickWhenAimingAtBlocks));
        props.setProperty("allowedServers", String.join(",", allowedServers));

        try (OutputStream out = Files.newOutputStream(configPath())) {
            props.store(out, "PvP Test Clicker. allowedServers = comma-separated server addresses where "
                    + "automated clicking is permitted (singleplayer is always allowed).");
        } catch (IOException ignored) {
        }
    }
}
