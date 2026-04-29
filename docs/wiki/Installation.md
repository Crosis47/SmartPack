# Installation

SmartPack runs on Paper servers and is built against the Paper API for Minecraft `1.21.11`.

## Requirements

| Requirement | Version |
| --- | --- |
| Server | Paper `1.21.11` |
| Java | `21` |
| Build tool | Maven 3.x, when building from source |

## Build From Source

Clone the repository and build the plugin jar:

```bash
mvn clean package
```

The compiled jar is created in `target/` as:

```text
smartpack-<version>.jar
```

## Install On A Server

1. Stop the server.
2. Place the SmartPack jar in the server's `plugins/` directory.
3. Start the server once.
4. Edit `plugins/SmartPack/config.yml` if needed.
5. Run `/pack reload` or restart the server after config changes.

## Files Created By The Plugin

| File | Purpose |
| --- | --- |
| `plugins/SmartPack/config.yml` | Main server configuration |
| `plugins/SmartPack/player-exclusions.db` | Persistent per-player exclusions and auto-pack preferences |

## First Configuration Checks

After the first startup, check these settings before opening the plugin to players:

| Setting | Why It Matters |
| --- | --- |
| `activation.mode` | Chooses `/pack` command mode or crafted Smart Packer item mode |
| `requirements.crafting_table_mode` | Controls when crafting tables are required |
| `auto_pack.enabled` | Enables or disables server-wide automatic packing |
| `pack.*` | Controls the actual material conversions |
| `validation.disable_non_reversible_recipes` | Can block custom recipes that do not appear reversible |

## Suggested Permission Setup

For a normal survival server:

| Group | Permissions |
| --- | --- |
| Players | `smartpack.use` |
| Trusted or donor groups | `smartpack.auto` |
| Staff | `smartpack.reload` |

`smartpack.use` defaults to `true`, `smartpack.auto` defaults to `false`, and `smartpack.reload` defaults to `op`.

## Updating

SmartPack merges new default config keys into existing configs and updates the internal `config-version` marker. Existing custom recipe entries are not intentionally replaced by the default file merge.

After updating:

1. Start the server or run `/pack reload`.
2. Watch the console for config warnings.
3. Review any new settings added to `config.yml`.
4. Test `/pack` with a few configured materials before announcing the update.
