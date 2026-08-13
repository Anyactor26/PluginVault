package com.example.hotpotato;

import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

/**
 * Repeating task that drives the hot-potato countdown. Runs once per second (every 20
 * ticks) and delegates to {@link GameManager#tick()}.
 *
 * <p>The task self-cancels when the game ends because {@code GameManager.tick()} calls
 * {@code stopGame()} which cancels the task reference held by the manager.
 */
public final class CountdownTask extends BukkitRunnable {

    private final GameManager gameManager;

    public CountdownTask(@NotNull GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public void run() {
        gameManager.tick();
    }
}
