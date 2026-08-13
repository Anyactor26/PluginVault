package com.example.hotpotato;

import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GameManager} core game-state logic.
 *
 * <p>These tests verify the in-memory state machine: starting a game, transferring the
 * potato, countdown expiry, disconnect handling, and stopping. Bukkit API objects are
 * mocked with Mockito so no live server is required.
 */
@ExtendWith(MockitoExtension.class)
class GameManagerTest {

    @Mock
    private JavaPlugin plugin;
    @Mock
    private Server server;
    @Mock
    private org.bukkit.entity.Player holder;
    @Mock
    private org.bukkit.entity.Player target;
    @Mock
    private org.bukkit.inventory.PlayerInventory holderInventory;
    @Mock
    private org.bukkit.inventory.PlayerInventory targetInventory;
    @Mock
    private org.bukkit.World world;
    @Mock
    private org.bukkit.Location location;
    @Mock
    private org.bukkit.configuration.file.FileConfiguration config;

    private GameManager gameManager;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getServer()).thenReturn(server);
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(config.getInt("countdown-seconds", 10)).thenReturn(10);
        lenient().when(config.getString("potato-display-name", "\uD83D\uDD25 HOT POTATO"))
                .thenReturn("\uD83D\uDD25 HOT POTATO");
        lenient().when(config.getString("transfer-sound", "ENTITY_GENERIC_EXPLODE"))
                .thenReturn("ENTITY_GENERIC_EXPLODE");
        lenient().when(config.getString("explosion-sound", "ENTITY_GENERIC_EXPLODE"))
                .thenReturn("ENTITY_GENERIC_EXPLODE");

        lenient().when(holder.getUniqueId()).thenReturn(UUID.randomUUID());
        lenient().when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        lenient().when(holder.getInventory()).thenReturn(holderInventory);
        lenient().when(target.getInventory()).thenReturn(targetInventory);
        lenient().when(holder.getLocation()).thenReturn(location);
        lenient().when(location.getX()).thenReturn(0.0);
        lenient().when(location.getY()).thenReturn(0.0);
        lenient().when(location.getZ()).thenReturn(0.0);
        lenient().when(holder.getWorld()).thenReturn(world);
        lenient().when(server.getOnlinePlayers()).thenReturn(java.util.Collections.emptyList());
        // Empty inventory contents so removePotatoFrom iterates without NPE.
        lenient().when(holderInventory.getContents()).thenReturn(new ItemStack[0]);
        lenient().when(targetInventory.getContents()).thenReturn(new ItemStack[0]);

        gameManager = new GameManager(plugin);
    }

    @Test
    void startGame_setsHolderActive() {
        assertFalse(gameManager.isActive());

        boolean result = gameManager.startGame(holder);

        assertTrue(result);
        assertTrue(gameManager.isActive());
        assertEquals(holder.getUniqueId(), gameManager.getHolderId());
        assertEquals(10, gameManager.getSecondsRemaining());
        verify(holderInventory).addItem(any());
    }

    @Test
    void startGame_rejectsSecondActiveGame() {
        gameManager.startGame(holder);

        boolean result = gameManager.startGame(target);

        assertFalse(result);
        assertEquals(holder.getUniqueId(), gameManager.getHolderId());
    }

    @Test
    void transferPotato_changesHolder() {
        gameManager.startGame(holder);

        boolean result = gameManager.transferPotato(target);

        assertTrue(result);
        assertEquals(target.getUniqueId(), gameManager.getHolderId());
        assertEquals(10, gameManager.getSecondsRemaining());
        // The previous holder's inventory is scanned for potato removal.
        verify(holderInventory).getContents();
        // The new holder receives the potato item.
        verify(targetInventory).addItem(any());
    }

    @Test
    void transferPotato_rejectsTransferToSelf() {
        gameManager.startGame(holder);

        boolean result = gameManager.transferPotato(holder);

        assertFalse(result);
        assertEquals(holder.getUniqueId(), gameManager.getHolderId());
    }

    @Test
    void transferPotato_rejectsWhenNoGameActive() {
        boolean result = gameManager.transferPotato(target);

        assertFalse(result);
        assertNull(gameManager.getHolderId());
    }

    @Test
    void countdownExpiry_triggersExplosionAndStopsGame() {
        // Use a 1-second countdown so a single tick triggers the explosion.
        when(config.getInt("countdown-seconds", 10)).thenReturn(1);
        when(server.getPlayer(holder.getUniqueId())).thenReturn(holder);

        gameManager.startGame(holder);
        assertTrue(gameManager.isActive());

        // First tick decrements to 0 and triggers explosion.
        gameManager.tick();

        assertFalse(gameManager.isActive());
        assertNull(gameManager.getHolderId());
        verify(world).createExplosion(0.0, 0.0, 0.0, 2.0f, false, false);
        verify(holder).setHealth(0.0);
    }

    @Test
    void countdownTick_decrementsAndUpdatesActionBar() {
        when(config.getInt("countdown-seconds", 10)).thenReturn(5);
        when(server.getPlayer(holder.getUniqueId())).thenReturn(holder);

        gameManager.startGame(holder);
        assertEquals(5, gameManager.getSecondsRemaining());

        gameManager.tick();

        assertEquals(4, gameManager.getSecondsRemaining());
        assertTrue(gameManager.isActive());
        verify(holder).sendActionBar(any());
    }

    @Test
    void disconnectHandling_clearsGameState() {
        gameManager.startGame(holder);
        assertTrue(gameManager.isActive());

        gameManager.handleDisconnect(holder);

        assertFalse(gameManager.isActive());
        assertNull(gameManager.getHolderId());
    }

    @Test
    void disconnectHandling_ignoresNonHolder() {
        gameManager.startGame(holder);
        UUID holderId = holder.getUniqueId();

        gameManager.handleDisconnect(target);

        assertTrue(gameManager.isActive());
        assertEquals(holderId, gameManager.getHolderId());
    }

    @Test
    void stopCommand_endsActiveGame() {
        gameManager.startGame(holder);
        assertTrue(gameManager.isActive());

        gameManager.stopGame();

        assertFalse(gameManager.isActive());
        assertNull(gameManager.getHolderId());
        assertEquals(0, gameManager.getSecondsRemaining());
    }

    @Test
    void stopGame_whenInactive_isNoOp() {
        assertFalse(gameManager.isActive());

        gameManager.stopGame();

        assertFalse(gameManager.isActive());
        assertNull(gameManager.getHolderId());
    }

    @Test
    void tick_whenHolderOffline_stopsGame() {
        gameManager.startGame(holder);
        when(server.getPlayer(holder.getUniqueId())).thenReturn(null);

        gameManager.tick();

        assertFalse(gameManager.isActive());
        assertNull(gameManager.getHolderId());
    }

    @Test
    void isPotatoItem_nullItem_returnsFalse() {
        assertFalse(gameManager.isPotatoItem(null));
    }
}
