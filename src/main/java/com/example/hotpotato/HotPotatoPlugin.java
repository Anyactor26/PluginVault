package com.example.hotpotato;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main entry point for the Hot-Potato plugin.
 *
 * <p>The plugin maintains a single in-memory hot-potato game. When enabled it loads the
 * default configuration, instantiates the {@link GameManager}, registers the command and
 * event listeners, and prepares the countdown task. On disable it stops any active game
 * and clears all potato items from inventories.
 */
public final class HotPotatoPlugin extends JavaPlugin {

    private GameManager gameManager;

    @Override
    public void onEnable() {
        // Save the default config.yml into the plugin data folder if absent.
        saveDefaultConfig();

        this.gameManager = new GameManager(this);

        // Register the /hotpotato command.
        HotPotatoCommand command = new HotPotatoCommand(gameManager);
        if (getCommand("hotpotato") != null) {
            getCommand("hotpotato").setExecutor(command);
            getCommand("hotpotato").setTabCompleter(command);
        } else {
            getLogger().severe("Command 'hotpotato' is not registered in plugin.yml; plugin will not function.");
        }

        // Register event listeners.
        getServer().getPluginManager().registerEvents(new PotatoListener(gameManager), this);

        getLogger().info("Hot-Potato enabled.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.stopGame();
        }
        getLogger().info("Hot-Potato disabled.");
    }

    /**
     * @return the active {@link GameManager} instance, or {@code null} before enable.
     */
    GameManager getGameManager() {
        return gameManager;
    }
}
