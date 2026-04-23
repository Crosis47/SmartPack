# Condense Reforged

Condense Reforged is a modernized hard fork of the original **MinecraftCondensePlugin** by **rd156**.

It keeps the original idea intact — condensing materials from a player's inventory with `/condense` — while adding better validation, clearer player messaging, in-game reload support, recipe safety checks, and configurable crafting table requirements.

## Highlights

- Supports modern **Paper 1.21.11** builds
- Builds for **Java 21** compatibility
- Uses configurable condense recipes
- Supports crafting table requirement modes
- Uses in-game item display names in player messages
- Shows leftovers in condense output
- Detects invalid or non-reversible configured recipes
- Supports `/condense reload`
- Includes upgrade-safe config merging and migration support

## Example output

Successful condense with leftovers:

```text
11 Iron Ingot → 1 Block of Iron + 2 Iron Ingot
```

Inventory-full failure with summary:

```text
Inventory full: 41 Iron Ingot → 4 Block of Iron + 5 Iron Ingot
Inventory full summary: 3 additional slot(s) would have been needed in total.
```

## Commands

### `/condense`
Attempts to condense every configured material found in the player's inventory.

### `/condense reload`
Reloads `config.yml`, reapplies defaults for any missing keys, and reruns validation.

## Permissions

- `condense.use` — allows use of `/condense`
- `condense.reload` — allows use of `/condense reload` (default: op)

## Crafting table requirement modes

Configured under:

```yaml
requirements:
  crafting_table_mode: INVENTORY_OR_NEARBY
  crafting_table_range: 5
```

Available modes:

- `DISABLED` — no crafting table requirement
- `INVENTORY_ONLY` — player must have a crafting table in inventory
- `NEARBY_ONLY` — player must be within range of a placed crafting table
- `INVENTORY_OR_NEARBY` — either condition is accepted

## Config features

The plugin supports:

- versioned config upgrades with `config-version`
- default merging for new config keys
- migration from older crafting-table boolean settings
- configurable player messages
- reversible recipe validation
- optional disabling of non-reversible recipes

## Example config structure

```yaml
config-version: 1

display:
  list: true

requirements:
  crafting_table_mode: INVENTORY_OR_NEARBY
  crafting_table_range: 5

validation:
  warn_if_not_reversible: true
  disable_non_reversible_recipes: false

message:
  reload: "§aCondense Reforged config reloaded."
  condense:
    item: "§a[item1] → [item2]"
    resume: "§a[number] output items were created."
  error:
    no_permission: "§cYou do not have permission to use this command."
    ratio_zero: "§4Attention: the conversion ratio of [item1] is invalid."
    inventory_full: "§4Inventory full: [item1] → [item2]"
    inventory_full_summary: "§6Inventory full summary: [slots] additional slot(s) would have been needed in total."
    nothing_to_condense: "§eYou do not have any valid materials to condense."
```

## Condense recipe format

```yaml
condense:
  IRON_INGOT:
    output: IRON_BLOCK
    ratio_in: 9
    ratio_out: 1
```

- `INPUT_MATERIAL` must be a valid Bukkit/Paper `Material`
- `output` must be a valid Bukkit/Paper `Material`
- `ratio_in` is the amount required
- `ratio_out` is the amount produced

## Current fork differences vs upstream

Compared to the original upstream project, this fork adds:

- new project identity and package namespace
- Paper 1.21.11 targeting
- Java 21 target compatibility
- reload command and tab completion
- better config comments and migration support
- crafting table requirement modes
- reversibility checks for recipes
- optional disabling of invalid/non-reversible rules
- improved inventory-full behavior
- leftover-aware messaging
- in-game display names instead of enum values

## Build

This project uses Maven.

```bash
mvn clean package
```

The plugin is built against the Paper API and should be compiled for Java 21 compatibility.

## License

This project remains under **GPL v3**, consistent with the upstream repository license.

## Attribution

Original upstream project:
- **MinecraftCondensePlugin** by **rd156**

This fork substantially extends the original implementation while preserving the original condense concept.
