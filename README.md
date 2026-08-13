# Hot-Potato

A Hot Potato minigame plugin for **Paper 1.21.4** (Java 21). Pass the potato before it explodes!

## Features

- 🥔 **Renamed potato item** with a custom display name and persistent marker
- 🤝 **Right-click a player** to transfer the potato
- ⏱️ **10-second countdown** (configurable) shown in the action bar
- 💥 **Explosion and death** when the countdown reaches zero (visual only — no block damage)
- 🎵 **Title and sound effects** on potato transfer
- 🔒 **Locked potato** — cannot be dropped, stored in containers, or moved in inventories
- 🚪 **Disconnect handling** — holder leaving cleanly ends the game
- 🎮 **Single active game** enforcement
- ⚙️ **Configurable** countdown duration, display name, and sounds

## Requirements

- Paper 1.21.4
- Java 21

## Building

```bash
./gradlew build
```

The compiled JAR will be at `build/libs/Hot-Potato-1.0.0.jar`.

## Installation

1. Download or build the JAR.
2. Place `Hot-Potato-1.0.0.jar` in your server's `plugins/` folder.
3. Restart the server.
4. (Optional) Edit `plugins/Hot-Potato/config.yml` to customize settings.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/hotpotato` | Start a Hot Potato game (you become the first holder) | `hotpotato.use` |
| `/hotpotato stop` | Stop the current Hot Potato game | `hotpotato.use` |

## Permissions

| Permission | Description | Default |
|---|---|---|
| `hotpotato.use` | Allows starting and stopping Hot Potato games | `op` |

## Configuration

Located at `plugins/Hot-Potato/config.yml`:

```yaml
# How many seconds the potato holder has before it explodes
countdown-seconds: 10

# Display name shown on the potato item (supports legacy color codes with &)
potato-display-name: "🔥 HOT POTATO"

# Sound played when the potato is transferred between players
transfer-sound: ENTITY_GENERIC_EXPLODE

# Sound played when the potato explodes
explosion-sound: ENTITY_GENERIC_EXPLODE
```

### Sound names

Use valid [Bukkit Sound enum](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html) names (e.g. `ENTITY_GENERIC_EXPLODE`, `BLOCK_NOTE_BLOCK_PLING`, `ENTITY_ENDERMAN_TELEPORT`). If an invalid sound is specified, the default (`ENTITY_GENERIC_EXPLODE`) is used.

## How it works

1. Run `/hotpotato` to start a game. You receive a renamed potato.
2. A countdown appears in your action bar.
3. **Right-click another player** to pass the potato. The countdown resets for the new holder.
4. If the countdown reaches zero, the holder explodes and dies (no block damage).
5. The potato cannot be dropped, stored, or moved — only passed by right-clicking.
6. If the holder disconnects or dies, the game ends.
7. Use `/hotpotato stop` to end the game at any time.

## Technical notes

- **No NMS or reflection** — uses only public Paper/Bukkit APIs.
- **No persistence** — game state is in-memory only.
- **Single repeating scheduler task** drives the countdown (every 20 ticks / 1 second).
- The potato item is tagged with a persistent-data-container key for reliable identification.
- Explosion is visual-only (`World#createExplosion` with `breakBlocks=false`).

## License

MIT — see [LICENSE](LICENSE).
