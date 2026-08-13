package com.example.hotpotato;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds all in-memory state for the single active hot-potato game and exposes the
 * operations the rest of the plugin needs.
 *
 * <p>This class is intentionally the single source of truth for game state so that the
 * command executor, listeners, and countdown task all coordinate through one object.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Only one game may be active at a time. {@link #startGame(Player)} rejects a second
 *       start while a game is running.</li>
 *   <li>The potato item is tagged with a persistent-data-container key so it can be
 *       reliably identified even if its display name is changed by other plugins.</li>
 *   <li>Countdown logic is split into {@link #tick()} (pure state mutation + side effects)
 *       so it can be unit-tested without a live scheduler.</li>
 * </ul>
 */
public final class GameManager {

    /** Persistent-data key used to mark the hot-potato item. */
    static final NamespacedKey POTATO_KEY = new NamespacedKey("hotpotato", "potato_marker");

    private final JavaPlugin plugin;

    /** Whether a game is currently running. */
    private boolean active;

    /** UUID of the player currently holding the potato, or {@code null} when inactive. */
    private UUID holderId;

    /** Seconds remaining before the potato explodes. */
    private int secondsRemaining;

    /** The repeating countdown task, or {@code null} when no game is running. */
    private BukkitTask countdownTask;

    public GameManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts a new hot-potato game with the given player as the initial holder.
     *
     * @param player the player to receive the potato
     * @return {@code true} if the game was started, {@code false} if one is already active
     */
    public synchronized boolean startGame(@NotNull Player player) {
        if (active) {
            return false;
        }
        active = true;
        holderId = player.getUniqueId();
        secondsRemaining = getCountdownSeconds();

        ItemStack potato = createPotatoItem();
        player.getInventory().addItem(potato);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        sendActionBar(player, secondsRemaining);

        startCountdownTask();
        return true;
    }

    /**
     * Stops the current game, removes the potato from all online players, and cancels the
     * countdown task. Safe to call when no game is running.
     */
    public synchronized void stopGame() {
        if (!active) {
            return;
        }
        clearPotatoFromAll();
        active = false;
        holderId = null;
        secondsRemaining = 0;
        cancelCountdownTask();
    }

    /**
     * Transfers the potato from the current holder to {@code target}.
     *
     * <p>The previous holder is marked safe: the potato item is removed from their inventory
     * and they receive a title indicating they are safe. The target receives the potato, the
     * countdown resets, and a transfer sound + title is played.
     *
     * @param target the player who will receive the potato
     * @return {@code true} if the transfer succeeded, {@code false} if no game is active or
     *         the target is the current holder
     */
    public synchronized boolean transferPotato(@NotNull Player target) {
        if (!active || holderId == null) {
            return false;
        }
        if (target.getUniqueId().equals(holderId)) {
            return false;
        }

        Player from = plugin.getServer().getPlayer(holderId);
        if (from == null) {
            // Holder went offline between events; give potato directly to target.
            holderId = target.getUniqueId();
            secondsRemaining = getCountdownSeconds();
            target.getInventory().addItem(createPotatoItem());
            playTransferEffects(null, target);
            sendActionBar(target, secondsRemaining);
            return true;
        }

        // Remove the potato from the previous holder.
        removePotatoFrom(from);

        // Give the potato to the new holder.
        target.getInventory().addItem(createPotatoItem());

        holderId = target.getUniqueId();
        secondsRemaining = getCountdownSeconds();

        playTransferEffects(from, target);
        sendActionBar(target, secondsRemaining);
        return true;
    }

    /**
     * Called once per second by the countdown task. Decrements the remaining time, updates
     * the action bar, and triggers the explosion when the countdown reaches zero.
     */
    synchronized void tick() {
        if (!active || holderId == null) {
            return;
        }
        Player holder = plugin.getServer().getPlayer(holderId);
        if (holder == null) {
            // Holder is offline; stop the game cleanly.
            stopGame();
            return;
        }

        secondsRemaining--;
        if (secondsRemaining <= 0) {
            explodeHolder(holder);
            stopGame();
            return;
        }
        sendActionBar(holder, secondsRemaining);
    }

    /**
     * Handles a holder disconnect: stops the game and clears state so a new game can start.
     */
    public synchronized void handleDisconnect(@NotNull Player player) {
        if (!active || holderId == null) {
            return;
        }
        if (player.getUniqueId().equals(holderId)) {
            stopGame();
        }
    }

    /**
     * Handles a holder death during the game: stops the game and clears state.
     */
    public synchronized void handleDeath(@NotNull Player player) {
        if (!active || holderId == null) {
            return;
        }
        if (player.getUniqueId().equals(holderId)) {
            stopGame();
        }
    }

    /** @return whether a game is currently active. */
    public synchronized boolean isActive() {
        return active;
    }

    /** @return the UUID of the current holder, or {@code null} when no game is active. */
    @Nullable
    public synchronized UUID getHolderId() {
        return holderId;
    }

    /** @return the seconds remaining on the current countdown (0 when inactive). */
    public synchronized int getSecondsRemaining() {
        return secondsRemaining;
    }

    /**
     * Determines whether the given item stack is the hot-potato item.
     *
     * @param item the item to test, may be {@code null} or {@link Material#AIR}
     * @return {@code true} if the item is tagged as the hot potato
     */
    public boolean isPotatoItem(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getType() != Material.POTATO) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(POTATO_KEY, PersistentDataType.BYTE);
    }

    /**
     * Creates a new hot-potato item stack with the configured display name and persistent
     * marker.
     *
     * @return a non-null potato item stack
     */
    @NotNull
    ItemStack createPotatoItem() {
        ItemStack potato = new ItemStack(Material.POTATO);
        ItemMeta meta = potato.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(getPotatoDisplayName())
                    .color(NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD));
            meta.getPersistentDataContainer().set(POTATO_KEY, PersistentDataType.BYTE, (byte) 1);
            potato.setItemMeta(meta);
        }
        return potato;
    }

    // ---- Internal helpers -------------------------------------------------

    private void startCountdownTask() {
        cancelCountdownTask();
        CountdownTask task = new CountdownTask(this);
        countdownTask = task.runTaskTimer(plugin, 20L, 20L); // every 20 ticks = 1 second
    }

    private void cancelCountdownTask() {
        if (countdownTask != null) {
            try {
                countdownTask.cancel();
            } catch (IllegalStateException ignored) {
                // Task may already be cancelled.
            }
            countdownTask = null;
        }
    }

    /**
     * Removes only the hot-potato item(s) from the given player's inventory. Iterates slot
     * by slot so that regular potatoes are left untouched.
     */
    private void removePotatoFrom(@NotNull Player player) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isPotatoItem(contents[i])) {
                inv.setItem(i, null);
            }
        }
    }

    /**
     * Removes the hot-potato item from every online player's inventory. Used on game stop
     * to guarantee no potato lingers after a game ends.
     */
    private void clearPotatoFromAll() {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            removePotatoFrom(online);
        }
    }

    private void explodeHolder(@NotNull Player holder) {
        Location loc = holder.getLocation();
        // Visual-only explosion: no block damage, no fire.
        holder.getWorld().createExplosion(loc.getX(), loc.getY(), loc.getZ(), 2.0f, false, false);
        holder.playSound(loc, getExplosionSound(), 1.0f, 1.0f);
        holder.setHealth(0.0);
    }

    private void playTransferEffects(@Nullable Player from, @NotNull Player to) {
        Sound transferSound = getTransferSound();
        to.playSound(to.getLocation(), transferSound, 1.0f, 1.0f);
        if (from != null) {
            from.playSound(from.getLocation(), transferSound, 1.0f, 1.0f);
        }

        // Title to the new holder.
        Component title = Component.text("HOT POTATO!", NamedTextColor.RED, TextDecoration.BOLD);
        Component subtitle = Component.text("Pass it on!", NamedTextColor.YELLOW);
        to.showTitle(Title.title(title, subtitle));

        // Title to the previous holder.
        if (from != null) {
            Component safeTitle = Component.text("SAFE!", NamedTextColor.GREEN, TextDecoration.BOLD);
            from.showTitle(Title.title(safeTitle, Component.empty()));
        }
    }

    private void sendActionBar(@NotNull Player player, int seconds) {
        Component message = Component.text("\uD83D\uDD25 Hot Potato: ", NamedTextColor.GOLD)
                .append(Component.text(seconds + "s", NamedTextColor.RED, TextDecoration.BOLD));
        player.sendActionBar(message);
    }

    // ---- Configuration accessors ------------------------------------------

    private int getCountdownSeconds() {
        return plugin.getConfig().getInt("countdown-seconds", 10);
    }

    private String getPotatoDisplayName() {
        return plugin.getConfig().getString("potato-display-name", "\uD83D\uDD25 HOT POTATO");
    }

    private Sound getTransferSound() {
        String name = plugin.getConfig().getString("transfer-sound", "ENTITY_GENERIC_EXPLODE");
        return matchSound(name, Sound.ENTITY_GENERIC_EXPLODE);
    }

    private Sound getExplosionSound() {
        String name = plugin.getConfig().getString("explosion-sound", "ENTITY_GENERIC_EXPLODE");
        return matchSound(name, Sound.ENTITY_GENERIC_EXPLODE);
    }

    private Sound matchSound(@NotNull String name, @NotNull Sound fallback) {
        try {
            return Sound.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
