package com.dsmppvp.linker;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public final class DSMPLinkPlugin {

    private HttpClient httpClient;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getLogger().info("DSMPLink enabled for Paper 1.21.11.");
    }

    public void showLinkDialog(Player player) {
        Dialog dialog = Dialog.create(builder -> builder
                .empty()
                .base(DialogBase.builder(Component.text("Link your Discord account"))
                        .canCloseWithEscape(false)
                        .body(List.of(
                                io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(
                                        Component.text("Enter the 6-digit code shown to you by the DSMP PvP Discord bot."))
                        ))
                        .inputs(List.of(
                                DialogInput.text(
                                        "code",
                                        300,
                                        Component.text("Discord code"),
                                        true,
                                        "",
                                        6,
                                        null
                                )
                        ))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(
                                Component.text("Link Account"),
                                Component.text("Verify your Discord code"),
                                150,
                                DialogAction.customClick(
                                        (view, audience) -> {
                                            if (!(audience instanceof Player target)) {
                                                return;
                                            }

                                            String code = view.getText("code");
                                            if (code == null) {
                                                target.sendMessage(Component.text("Please enter your 6-digit Discord code."));
                                                return;
                                            }

                                            code = code.trim();
                                            if (!code.matches("\\d{6}")) {
                                                target.sendMessage(Component.text("Your code must be exactly 6 digits."));
                                                Bukkit.getScheduler().runTask(this, () -> showLinkDialog(target));
                                                return;
                                            }

                                            verifyCode(target, code);
                                        },
                                        null
                                )
                        ),
                        ActionButton.create(
                                Component.text("Cancel"),
                                Component.text("Close the linking screen"),
                                150,
                                null
                        )
                ))
        );

        player.showDialog(dialog);
    }

    private void verifyCode(Player player, String code) {
        player.sendMessage(Component.text("Linking your account..."));

        String apiUrl = getConfig().getString("api-url", "").trim();
        String secret = getConfig().getString("api-secret", "").trim();

        if (apiUrl.isEmpty() || apiUrl.contains("CHANGE-ME")) {
            player.sendMessage(Component.text("The linking system is not configured yet."));
            getLogger().warning("api-url is not configured in config.yml.");
            return;
        }

        String json = "{"
                + "\"code\":\"" + escapeJson(code) + "\","
                + "\"minecraftUuid\":\"" + escapeJson(player.getUniqueId().toString()) + "\","
                + "\"minecraftName\":\"" + escapeJson(player.getName()) + "\""
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("X-DSMP-Link-Secret", secret)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null) {
                        getLogger().warning("Link API request failed: " + error.getMessage());
                        player.sendMessage(Component.text("Could not contact the Discord linking service. Try again in a moment."));
                        return;
                    }

                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        player.sendMessage(Component.text("The linking service rejected the request. Try again."));
                        getLogger().warning("Link API returned HTTP " + response.statusCode());
                        return;
                    }

                    String body = response.body();
                    if (body.contains("\"success\":true")) {
                        player.sendMessage(Component.text("Your Minecraft and Discord accounts are now linked!"));
                    } else if (body.contains("CODE_EXPIRED")) {
                        player.sendMessage(Component.text("That code has expired. Press Cancel and request a new code on Discord."));
                    } else if (body.contains("CODE_INVALID")) {
                        player.sendMessage(Component.text("That code is invalid. Check the code and try again."));
                        Bukkit.getScheduler().runTaskLater(this, () -> showLinkDialog(player), getConfig().getLong("reopen-delay-seconds", 2) * 20L);
                    } else if (body.contains("ALREADY_LINKED")) {
                        player.sendMessage(Component.text("This Minecraft account is already linked."));
                    } else {
                        player.sendMessage(Component.text("The code could not be verified. Please try again."));
                    }
                }));
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
