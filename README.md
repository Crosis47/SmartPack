<p align="center">
  <img src="docs/branding/smartpack-hero.png" alt="SmartPack branding banner" width="900">
</p>

<h1 align="center">SmartPack</h1>

<p align="center">
  <strong>Pack more. Carry less. Play smarter.</strong>
  <br>
  A Paper plugin for fast, safe Minecraft inventory compression with <code>/pack</code>.
</p>

SmartPack packs configured materials directly from a player's inventory, turning loose resources into compact storage blocks without risky manual crafting loops.

It is built for storage-style conversions such as nuggets to ingots, ingots to blocks, redstone to redstone blocks, and other server-configured recipes. SmartPack adds practical guardrails around those conversions: configurable crafting-table requirements, reversible-recipe validation, inventory safety checks, player exclusions, optional auto-packing, and reloadable config.

This project is a fork of the original MinecraftCondensePlugin by `rd156`.

<p align="center">
  <img src="docs/branding/smartpack-features.png" alt="SmartPack feature summary" width="900">
</p>

## Why SmartPack

- **Smart compression:** Compress materials and free up inventory space.
- **Multiple ways to pack:** Use commands, a crafted item, or automation depending on server preference.
- **Automate the process:** Let permitted players auto-pack items while they play.
- **Safe and reliable:** Simulates inventory changes before applying them to prevent item loss.
- **Play smarter:** Reduce clutter and make every slot count.

## What It Does

- Packs items using recipes defined in `config.yml`
- Works directly from the player's inventory
- Supports both command activation and Smart Packer item activation
- Supports optional trigger-based auto-packing with actionbar feedback
- Supports optional crafting-table requirements
- Can allow small recipes to bypass the crafting-table requirement
- Lets each player exclude configured materials through `/pack exclude`
- Simulates inventory changes before applying them to prevent item loss
- Can include nearby pickup items in inventory-full slot estimates
- Automatically removes leftover Smart Packer items while running in `COMMAND` mode
- Warns about non-reversible recipes and can disable them automatically
- Reloads configuration in game with `/pack reload`

## Requirements

- Java 21
- Paper 1.21.11
- Maven 3.x to build from source

The plugin is built against `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`.

## Build

```bash
mvn clean package
```

The built jar will be created in `target/` as `smartpack-<version>.jar`.

## Install

1. Build the jar with Maven or use a packaged release.
2. Place the jar in your server's `plugins/` directory.
3. Start the server once to generate `plugins/SmartPack/config.yml`.
4. Adjust the config if needed.
5. Run `/pack reload` or restart the server after changes.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/pack` | Pack any configured materials the player is carrying | `smartpack.use` |
| `/pack exclude` | Open a GUI to toggle which configured inputs should be skipped for that player | `smartpack.use` |
| `/pack auto` | Toggle automatic packing for the player | `smartpack.use` + `smartpack.auto` |
| `/pack reload` | Reload the plugin configuration | `smartpack.reload` |

`/pack` is player-only. `/pack reload` can be run by any sender with permission.

If `activation.mode` is set to `SMART_PACKER_ITEM`, regular packing is triggered by right-clicking the special Smart Packer item in the player's own inventory.

In item mode, shift-right-clicking the Smart Packer item in the player's inventory enables that player's saved auto-pack preference when auto-pack is available for the server and mode.

Admins can also enable `/pack` in item mode with `activation.smart_packer_item.allow_command_with_item: true`. When that toggle is on, the command only works if the player is carrying a Smart Packer.

## Permissions

| Permission | Description | Default |
| --- | --- | --- |
| `smartpack.use` | Allows players to use `/pack` | `true` |
| `smartpack.auto` | Allows automatic packing when `auto_pack.enabled` is true | `false` |
| `smartpack.reload` | Allows reloading the plugin config | `op` |

## How Packing Works

When a player runs `/pack`, SmartPack:

1. Loads the configured recipe list from `config.yml`.
2. Checks whether the player's crafting-table requirement is satisfied for each recipe.
3. Counts matching materials in the player's inventory.
4. Simulates removing the inputs and adding the outputs before changing the real inventory.
5. Applies successful conversions and repeats until no more configured conversions can run.

<table>
  <tr>
    <td width="50%">
      <img src="docs/screenshots/1.png" alt="Iron ingots packed into iron blocks with chat summary" width="100%">
      <br><strong>Fast inventory packing</strong><br>
      Large stacks compress directly from the player inventory, with chat summarizing the final result.
    </td>
    <td width="50%">
      <img src="docs/screenshots/2.png" alt="Gold nuggets packed through ingots into gold blocks" width="100%">
      <br><strong>Multi-tier compression</strong><br>
      Tiered recipes can continue in one run, such as nuggets turning into blocks with leftovers preserved.
    </td>
  </tr>
</table>

If the output would not fit, the plugin leaves the inventory unchanged for that conversion and reports how many extra slots would have been needed.

<p align="center">
  <img src="docs/screenshots/10.png" alt="Inventory full warning after attempting to pack redstone" width="820">
  <br><strong>Inventory-full safety</strong><br>
  The plugin reports the blocked conversion and estimates how many extra slots would be needed.
</p>

When `inventory_full.include_nearby_pickups` is enabled, that slot estimate also includes item entities already within the configured nearby pickup radius. The estimate packs those nearby materials with the same configured recipes, exclusions, crafting-table rules, and multi-tier chaining used by the main pack flow before counting the final slots needed.

Players can also open `/pack exclude` to choose configured input materials that should be ignored:

<p align="center">
  <img src="docs/screenshots/3.png" alt="SmartPack Exclusions menu showing configured inputs" width="540">
  <br><strong>SmartPack Exclusions menu</strong>
</p>

- Left-click marks a material to be skipped on the next pack run only.
- Right-click toggles a persistent per-player exclusion saved in `player-exclusions.db`.
- Glowing slots indicate that an exclusion is currently active for that material.
- Closing the menu normally, or using the green apply button, keeps the current changes and closes the menu.
- The red `X` button in the bottom-right corner cancels all edits made during the current menu session.
- After each pack cycle, the plugin reports any excluded configured inputs that were left skipped in the player's current storage inventory.

<table>
  <tr>
    <td width="50%">
      <img src="docs/screenshots/4-1.png" alt="Tooltip for a normal non-excluded wheat entry" width="100%">
      <br><strong>Normal input state</strong><br>
      Regular entries show the available one-time and persistent exclusion actions.
    </td>
    <td width="50%">
      <img src="docs/screenshots/4-2.png" alt="Tooltip for a temporary next-run exclusion" width="100%">
      <br><strong>Next-run skip</strong><br>
      Yellow marked entries are skipped once, then automatically return to normal.
    </td>
  </tr>
  <tr>
    <td width="50%">
      <img src="docs/screenshots/4-3.png" alt="Tooltip for a persistent copper ingot exclusion" width="100%">
      <br><strong>Persistent exclusion</strong><br>
      Red marked entries remain excluded across future pack runs until toggled back on.
    </td>
    <td width="50%">
      <img src="docs/screenshots/5.png" alt="Exclusion Help tooltip explaining menu controls" width="100%">
      <br><strong>Built-in menu help</strong><br>
      The help item explains left-click, right-click, glowing slots, apply, and cancel behavior.
    </td>
  </tr>
  <tr>
    <td width="50%">
      <img src="docs/screenshots/6.png" alt="Cancel Changes tooltip in the exclusion menu" width="100%">
      <br><strong>Apply and cancel controls</strong><br>
      The bottom-row controls let players keep their current edits or cancel everything from the menu session.
    </td>
    <td width="50%">
      <img src="docs/screenshots/7.png" alt="Excluded iron nuggets skipped while gold nuggets pack" width="100%">
      <br><strong>Skipped excluded materials</strong><br>
      Excluded inputs stay in the inventory and the player gets a clear skipped-materials message.
    </td>
  </tr>
</table>

## Auto-Pack

When `auto_pack.enabled` is true, players with both `smartpack.use` and `smartpack.auto` can automatically pack configured materials shortly after supported triggers. By default, this includes picking up item entities and, when command-mode crafting-table requirements accept nearby tables, placing a crafting table or entering the configured crafting-table range.

Auto-pack uses the same conversion flow as manual activation, including configured recipes, reversible-recipe disabling, inventory simulation, multi-tier chaining, inventory-full checks, and player exclusions. Persistent exclusions are always respected. Next-run exclusions also apply to the next automatic run because they apply to the next pack cycle.

Players can use `/pack auto` to toggle automatic packing for themselves. In Smart Packer item mode, they can also shift-right-click the Smart Packer item in their inventory to enable auto-pack. The preference is saved in `player-exclusions.db`; before a player changes it, `auto_pack.default_enabled` controls their default state and defaults to `false`. The server-wide `auto_pack.enabled` setting, current-mode auto-pack setting, and `smartpack.auto` permission are still required.

Successful automatic runs send one final actionbar message using `message.auto_pack.actionbar`. If a conversion cannot safely fit output, the player receives an actionbar warning using `message.auto_pack.inventory_full_actionbar`, throttled by `auto_pack.feedback.inventory_full_cooldown_ticks`.

In `COMMAND` mode, auto-pack follows the normal `requirements.*` crafting-table rules. In `SMART_PACKER_ITEM` mode, auto-pack is disabled by default; if enabled, it follows item-mode behavior by ignoring `requirements.*` and can require the player to carry a Smart Packer item.

The nearby crafting-table triggers use `requirements.crafting_table_range` and only run when `requirements.crafting_table_mode` is `NEARBY_ONLY` or `INVENTORY_OR_NEARBY`. When `INVENTORY_OR_NEARBY` is already satisfied by a crafting table in the player's inventory, the nearby trigger checks are skipped.

When `activation.mode` is `SMART_PACKER_ITEM`, the same packing flow is triggered by right-clicking the Smart Packer item in the player's inventory. The Smart Packer is a custom crafting table item with a glint and a configurable recipe.

In Smart Packer item mode, the plugin ignores the `requirements.*` crafting-table checks entirely. The Smart Packer item itself is also not placeable as a block.

## Activation Modes

`activation.mode` supports two modes:

| Mode | Behavior |
| --- | --- |
| `COMMAND` | Players use `/pack` |
| `SMART_PACKER_ITEM` | Players use a crafted Smart Packer item in their inventory |

The Smart Packer recipe is only registered with the server when `activation.mode` is `SMART_PACKER_ITEM`.

When the plugin is running in `COMMAND` mode, it automatically removes leftover Smart Packer items from player inventories on startup, on `/pack reload`, and when players join.

`activation.smart_packer_item.allow_command_with_item` controls whether `/pack` is also available in item mode:

- `false`: players must right-click the Smart Packer in inventory
- `true`: players can either right-click the Smart Packer or use `/pack`, but only while carrying a Smart Packer

The Smart Packer item tooltip lists both item interactions:

- Instant mode: right-click to pack carried materials now
- Auto mode: shift-right-click to enable pickup packing

<table>
  <tr>
    <td width="50%">
      <img src="docs/screenshots/11.png" alt="Smart Packer crafting recipe in the crafting table interface" width="100%">
      <br><strong>Crafted Smart Packer item</strong><br>
      In item mode, the configured recipe creates a named Smart Packer item with a glint.
    </td>
    <td width="50%">
      <img src="docs/screenshots/12.png" alt="Smart Packer item in inventory after packing materials" width="100%">
      <br><strong>Smart Packer activation</strong><br>
      Right-clicking the Smart Packer from the player inventory runs the same packing flow.
    </td>
  </tr>
</table>

## Crafting Table Requirement Modes

`requirements.crafting_table_mode` supports four modes:

| Mode | Behavior |
| --- | --- |
| `DISABLED` | No crafting table is required |
| `INVENTORY_ONLY` | The player must carry a crafting table |
| `NEARBY_ONLY` | The player must be within range of a placed crafting table |
| `INVENTORY_OR_NEARBY` | Either condition is accepted |

These settings only apply when `activation.mode` is `COMMAND`.

<table>
  <tr>
    <td width="50%">
      <img src="docs/screenshots/9.png" alt="Packing near a crafting table succeeds" width="100%">
      <br><strong>Nearby crafting table accepted</strong><br>
      When nearby-table mode is enabled, standing within range lets larger recipes run.
    </td>
    <td width="50%">
      <img src="docs/screenshots/8.png" alt="Crafting table required error message" width="100%">
      <br><strong>Out-of-range protection</strong><br>
      Players get a clear message when a recipe requires a crafting table and none is close enough.
    </td>
  </tr>
</table>

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

The bundled `config.yml` includes reversible vanilla-style compression recipes ordered by input material family:

- `BONE_MEAL -> BONE_BLOCK`
- `COAL -> COAL_BLOCK`
- `COPPER_NUGGET -> COPPER_INGOT`
- `COPPER_INGOT -> COPPER_BLOCK`
- `DIAMOND -> DIAMOND_BLOCK`
- `DRIED_KELP -> DRIED_KELP_BLOCK`
- `EMERALD -> EMERALD_BLOCK`
- `GOLD_NUGGET -> GOLD_INGOT`
- `GOLD_INGOT -> GOLD_BLOCK`
- `IRON_NUGGET -> IRON_INGOT`
- `IRON_INGOT -> IRON_BLOCK`
- `LAPIS_LAZULI -> LAPIS_BLOCK`
- `NETHERITE_INGOT -> NETHERITE_BLOCK`
- `RAW_COPPER -> RAW_COPPER_BLOCK`
- `RAW_GOLD -> RAW_GOLD_BLOCK`
- `RAW_IRON -> RAW_IRON_BLOCK`
- `REDSTONE -> REDSTONE_BLOCK`
- `RESIN_CLUMP -> RESIN_BLOCK`
- `SLIME_BALL -> SLIME_BLOCK`
- `WHEAT -> HAY_BLOCK`

You can add or remove entries under the `pack:` section to customize the behavior.

## Configuration Overview

The main config sections are:

- `config-version`: internal config format marker
- `activation.*`: command vs Smart Packer item activation, including whether `/pack` is allowed in item mode
- `auto_pack.*`: optional automatic packing triggers, cooldowns, mode policy, and actionbar feedback
- `display.list`: enables one final chat line per original packed input after the pack cycle completes
- `inventory_full.*`: controls inventory-full slot estimation, including nearby pickup item scanning
- `requirements.*`: crafting-table requirement behavior
- `validation.*`: reversible recipe warnings and disabling
- `message.*`: all user-facing plugin messages
- `pack.*`: the actual conversion recipes

Recipe format:

```yml
pack:
  IRON_INGOT:
    output: IRON_BLOCK
    ratio_in: 9
    ratio_out: 1
```

## Message Placeholders

The configurable messages use these placeholders:

- `[item1]`: original input amount and material for a completed pack line
- `[item2]`: final packed result for that original input, including leftovers
- `[input]`: total original input count packed in the final summary
- `[output]`: total final item count after packing in the final summary
- `[items]`: combined final packed materials if the summary template uses a material list
- `[slots]`: number of additional inventory slots needed
- `[range]`: configured crafting-table search range
- `[mode]`: invalid crafting-table mode from config

Inventory-full settings:

- `inventory_full.include_nearby_pickups`: includes already-nearby item entities in slot estimates when a pack result cannot fit, after simulating any configured packing they can undergo
- `inventory_full.pickup_radius`: radius used for the nearby item scan

## Notes For Server Owners

- Invalid materials or invalid ratios are logged and skipped.
- Non-item materials are rejected during validation.
- Persistent player exclusions are stored in `player-exclusions.db`.

## Project Layout

```text
src/main/java/crosis47/minecraft/smartpack/
  SmartPack.java
  commands/PackCommand.java
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
