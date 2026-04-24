# Condense Reforged

Condense Reforged is a Paper plugin that lets players condense configured materials directly from their inventory with `/condense`.

The plugin is designed for storage-style conversions such as nuggets to ingots or ingots to blocks, while adding guardrails that the original project did not have: configurable crafting-table requirements, reversible-recipe validation, inventory safety checks, and reloadable config.

This project is a fork of the original MinecraftCondensePlugin by `rd156`.

## What It Does

- Condenses items using recipes defined in `config.yml`
- Works directly from the player's inventory
- Supports both command activation and Condenser item activation
- Supports optional crafting-table requirements
- Can allow small recipes to bypass the crafting-table requirement
- Lets each player exclude configured materials through `/condense exclude`
- Simulates inventory changes before applying them to prevent item loss
- Automatically removes leftover Condenser items while running in `COMMAND` mode
- Warns about non-reversible recipes and can disable them automatically
- Reloads configuration in game with `/condense reload`

## Requirements

- Java 21
- Paper 1.21.11
- Maven 3.x to build from source

The plugin is built against `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`.

## Build

```bash
mvn clean package
```

The built jar will be created in `target/` as `condense-reforged-<version>.jar`.

## Install

1. Build the jar with Maven or use a packaged release.
2. Place the jar in your server's `plugins/` directory.
3. Start the server once to generate `plugins/CondenseReforged/config.yml`.
4. Adjust the config if needed.
5. Run `/condense reload` or restart the server after changes.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/condense` | Condense any configured materials the player is carrying | `condense.use` |
| `/condense exclude` | Open a GUI to toggle which configured inputs should be skipped for that player | `condense.use` |
| `/condense reload` | Reload the plugin configuration | `condense.reload` |

`/condense` is player-only. `/condense reload` can be run by any sender with permission.

If `activation.mode` is set to `CONDENSER_ITEM`, regular condensing is triggered by right-clicking the special Condenser item in the player's own inventory.

Admins can also enable `/condense` in item mode with `activation.condenser_item.allow_command_with_item: true`. When that toggle is on, the command only works if the player is carrying a Condenser.

## Permissions

| Permission | Description | Default |
| --- | --- | --- |
| `condense.use` | Allows players to use `/condense` | `true` |
| `condense.reload` | Allows reloading the plugin config | `op` |

## How Condensing Works

When a player runs `/condense`, the plugin:

1. Loads the configured recipe list from `config.yml`.
2. Checks whether the player's crafting-table requirement is satisfied for each recipe.
3. Counts matching materials in the player's inventory.
4. Simulates removing the inputs and adding the outputs before changing the real inventory.
5. Applies successful conversions and repeats until no more configured conversions can run.

If the output would not fit, the plugin leaves the inventory unchanged for that conversion and reports how many extra slots would have been needed.

Players can also open `/condense exclude` to choose configured input materials that should be ignored:

- Left-click marks a material to be skipped on the next condense run only.
- Right-click toggles a persistent per-player exclusion saved in `player-exclusions.db`.
- Glowing slots indicate that an exclusion is currently active for that material.
- Closing the menu normally, or using the green apply button, keeps the current changes and closes the menu.
- The red `X` button in the bottom-right corner cancels all edits made during the current menu session.
- After each condense cycle, the plugin reports any excluded configured inputs that were left skipped in the player's current storage inventory.

When `activation.mode` is `CONDENSER_ITEM`, the same condense flow is triggered by right-clicking the Condenser item in the player's inventory. The Condenser is a custom crafting table item with a glint and a configurable recipe.

In Condenser item mode, the plugin ignores the `requirements.*` crafting-table checks entirely. The Condenser item itself is also not placeable as a block.

## Activation Modes

`activation.mode` supports two modes:

| Mode | Behavior |
| --- | --- |
| `COMMAND` | Players use `/condense` |
| `CONDENSER_ITEM` | Players use a crafted Condenser item in their inventory |

The Condenser recipe is only registered with the server when `activation.mode` is `CONDENSER_ITEM`.

When the plugin is running in `COMMAND` mode, it automatically removes leftover Condenser items from player inventories on startup, on `/condense reload`, and when players join.

`activation.condenser_item.allow_command_with_item` controls whether `/condense` is also available in item mode:

- `false`: players must right-click the Condenser in inventory
- `true`: players can either right-click the Condenser or use `/condense`, but only while carrying a Condenser

## Crafting Table Requirement Modes

`requirements.crafting_table_mode` supports four modes:

| Mode | Behavior |
| --- | --- |
| `DISABLED` | No crafting table is required |
| `INVENTORY_ONLY` | The player must carry a crafting table |
| `NEARBY_ONLY` | The player must be within range of a placed crafting table |
| `INVENTORY_OR_NEARBY` | Either condition is accepted |

These settings only apply when `activation.mode` is `COMMAND`.

Related settings:

- `requirements.crafting_table_range`: search radius for nearby crafting tables
- `requirements.bypass_crafting_table_for_small_recipes`: lets small recipes ignore the requirement
- `requirements.small_recipe_bypass_max_ratio_in`: maximum `ratio_in` that qualifies as a small recipe

## Reversible Recipe Validation

The plugin validates configured recipes during startup and reload:

- `validation.warn_if_not_reversible`: logs a warning when a configured recipe does not appear reversible based on the server's loaded crafting recipes
- `validation.disable_non_reversible_recipes`: prevents those recipes from running at all

This is useful if your server has datapacks or custom recipes and you only want storage-style conversions that can be crafted back cleanly.

## Default Recipe Set

The bundled `config.yml` includes reversible vanilla-style compression recipes for:

- `IRON_NUGGET -> IRON_INGOT`
- `GOLD_NUGGET -> GOLD_INGOT`
- `IRON_INGOT -> IRON_BLOCK`
- `GOLD_INGOT -> GOLD_BLOCK`
- `DIAMOND -> DIAMOND_BLOCK`
- `EMERALD -> EMERALD_BLOCK`
- `LAPIS_LAZULI -> LAPIS_BLOCK`
- `REDSTONE -> REDSTONE_BLOCK`
- `COAL -> COAL_BLOCK`
- `NETHERITE_INGOT -> NETHERITE_BLOCK`
- `COPPER_INGOT -> COPPER_BLOCK`
- `RAW_IRON -> RAW_IRON_BLOCK`
- `RAW_GOLD -> RAW_GOLD_BLOCK`
- `RAW_COPPER -> RAW_COPPER_BLOCK`
- `SLIME_BALL -> SLIME_BLOCK`
- `WHEAT -> HAY_BLOCK`
- `BONE_MEAL -> BONE_BLOCK`
- `DRIED_KELP -> DRIED_KELP_BLOCK`

You can add or remove entries under the `condense:` section to customize the behavior.

## Configuration Overview

The main config sections are:

- `config-version`: internal migration/version marker
- `activation.*`: command vs Condenser-item activation, including whether `/condense` is allowed in item mode
- `display.list`: enables per-conversion chat output
- `requirements.*`: crafting-table requirement behavior
- `validation.*`: reversible recipe warnings and disabling
- `message.*`: all user-facing plugin messages
- `condense.*`: the actual conversion recipes

Recipe format:

```yml
condense:
  IRON_INGOT:
    output: IRON_BLOCK
    ratio_in: 9
    ratio_out: 1
```

## Message Placeholders

The configurable messages use these placeholders:

- `[item1]`: consumed input amount and material
- `[item2]`: produced output, plus leftover input when applicable
- `[input]`: total input count used in the final summary
- `[output]`: total output count used in the final summary
- `[slots]`: number of additional inventory slots needed
- `[range]`: configured crafting-table search range
- `[mode]`: invalid crafting-table mode from config

## Notes For Server Owners

- Invalid materials or invalid ratios are logged and skipped.
- Non-item materials are rejected during validation.
- Legacy crafting-table settings are migrated to `requirements.crafting_table_mode`.
- Persistent player exclusions are stored in `player-exclusions.db`.
- On first startup after upgrading, any existing `player-exclusions.yml` data is migrated into SQLite and the legacy file is backed up as `player-exclusions.yml.bak`.

## Project Layout

```text
src/main/java/crosis47/minecraft/condense/
  CondensePlugin.java
  commands/CondenseCommand.java
  storage/PlayerExclusionStore.java
  requirements/CraftingTableMode.java

src/main/resources/
  plugin.yml
  config.yml
```

## License

This project is licensed under the GNU General Public License v3.0. See [License.md](License.md).

## Credits

- Original project: [rd156/MinecraftCondensePlugin](https://github.com/rd156/MinecraftCondensePlugin)
- Fork and current maintenance: `crosis47`
