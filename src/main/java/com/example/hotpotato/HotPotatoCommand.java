package com.example.hotpotato;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Handles the {@code /hotpotato} command.
 *
 * <p>Usage:
 * <ul>
 *   <li>{@code /hotpotato} - starts a new hot-potato game with the sender as holder.</li>
 *   <li>{@code /hotpotato stop} - stops the current game.</li>
 * </ul>
 *
 * <p>Permission {@code hotpotato.use} is enforced by the plugin.yml declaration and is also
 * checked explicitly here for defense in depth.
 */
public final class HotPotatoCommand implements CommandExecutor, TabCompleter {

    private final GameManager gameManager;

    public HotPotatoCommand(@NotNull GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("hotpotato.use")) {
            sender.sendMessage("\u00a7cYou do not have permission to use this command.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("stop")) {
            if (!gameManager.isActive()) {
                sender.sendMessage("\u00a7eThere is no active Hot Potato game to stop.");
            } else {
                gameManager.stopGame();
                sender.sendMessage("\u00a7aHot Potato game stopped.");
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("\u00a7cOnly players can start a Hot Potato game.");
            return true;
        }

        if (gameManager.isActive()) {
            sender.sendMessage("\u00a7cA Hot Potato game is already running. Use /hotpotato stop first.");
            return true;
        }

        if (gameManager.startGame(player)) {
            sender.sendMessage("\u00a7aHot Potato game started! Pass the potato before it explodes!");
        } else {
            sender.sendMessage("\u00a7cFailed to start the Hot Potato game.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("hotpotato.use")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
            if ("stop".startsWith(prefix)) {
                return Collections.singletonList("stop");
            }
        }
        return Collections.emptyList();
    }
}
