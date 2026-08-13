package com.example.hotpotato;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for all events related to the hot-potato item and game lifecycle.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Right-click on another player transfers the potato.</li>
 *   <li>Cancel dropping the potato.</li>
 *   <li>Cancel moving the potato in inventories.</li>
 *   <li>Cancel hoppers/minecarts moving the potato.</li>
 *   <li>Cancel container interactions while holding the potato.</li>
 *   <li>Handle holder disconnect and death.</li>
 * </ul>
 */
public final class PotatoListener implements Listener {

    private final GameManager gameManager;

    public PotatoListener(@NotNull GameManager gameManager) {
        this.gameManager = gameManager;
    }

    /**
     * Right-clicking another player transfers the potato from the current holder to the
     * clicked player.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        if (!gameManager.isActive()) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player clicker = event.getPlayer();
        if (!clicker.getUniqueId().equals(gameManager.getHolderId())) {
            return;
        }
        // Verify the clicker actually has the potato in hand.
        ItemStack mainHand = clicker.getInventory().getItemInMainHand();
        ItemStack offHand = clicker.getInventory().getItemInOffHand();
        if (!gameManager.isPotatoItem(mainHand) && !gameManager.isPotatoItem(offHand)) {
            return;
        }
        if (gameManager.transferPotato(target)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent the potato from being dropped on the ground.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDropItem(@NotNull PlayerDropItemEvent event) {
        if (gameManager.isPotatoItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent the potato from being moved within or between inventories via clicks.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        // Cancel if the current item (being moved) is the potato.
        ItemStack current = event.getCurrentItem();
        if (gameManager.isPotatoItem(current)) {
            event.setCancelled(true);
            return;
        }
        // Cancel if the cursor item (being placed) is the potato.
        ItemStack cursor = event.getCursor();
        if (gameManager.isPotatoItem(cursor)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent hoppers and minecarts from moving the potato into containers.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(@NotNull InventoryMoveItemEvent event) {
        if (gameManager.isPotatoItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent the holder from opening containers while holding the potato, which avoids
     * edge cases where the item could end up stored.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (!gameManager.isActive()) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(gameManager.getHolderId())) {
            return;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (gameManager.isPotatoItem(mainHand) || gameManager.isPotatoItem(offHand)) {
            if (event.getClickedBlock().getState() instanceof org.bukkit.inventory.InventoryHolder) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Handle the holder disconnecting: stop the game and clear state.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        gameManager.handleDisconnect(event.getPlayer());
    }

    /**
     * Handle the holder dying during the game: remove the potato from death drops to
     * prevent duplication/loss, then stop the game and clear state.
     *
     * <p>Note: {@link PlayerDeathEvent} is not cancellable, so {@code ignoreCancelled} is
     * not used here.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        // Remove any potato items from the drops to prevent duplication/loss.
        event.getDrops().removeIf(gameManager::isPotatoItem);
        gameManager.handleDeath(event.getEntity());
    }
}
