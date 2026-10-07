package com.dsmppvp.linker;

import org.bukkit.entity.Player;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class JoinListener implements Listener {
    private final DSMPLinkPlugin plugin;

    public JoinListener(DSMPLinkPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("require-link-on-join", true)) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.showLinkDialog(player);
            }
        }, 10L);
    }
    @EventHandler
    public void onDialogSubmit(PlayerCustomClickEvent event) {
        if (!event.getIdentifier().equals(Key.key("dsmppvp:link"))) return;
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) return;
        DialogResponseView view = event.getDialogResponseView();
        if (view == null) return;
        Player player = connection.getPlayer();
        String code = view.getText("code");
        if (code == null || code.isBlank()) {
            player.sendMessage("Please enter your 6-digit Discord code.");
            return;
        }
        code = code.trim();
        if (!code.matches("\\d{6}")) {
            player.sendMessage("Your code must be exactly 6 digits.");
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.showLinkDialog(player));
            return;
        }
        plugin.verifyCode(player, code);
    }

}
