# Configuration

SmartPack is configured in `plugins/SmartPack/config.yml`.

The bundled default config is organized into major sections so server owners can decide how packing is triggered, what recipes exist, what requirements apply, and how messages are shown.

## Main Sections

| Section | Purpose |
| --- | --- |
| `config-version` | Internal config format marker |
| `activation.*` | Command mode vs Smart Packer item mode |
| `requirements.*` | Crafting-table requirement behavior |
| `auto_pack.*` | Automatic packing triggers, cooldowns, and feedback |
| `display.*` | Manual packing display controls |
| `validation.*` | Recipe validation and reversible-recipe checks |
| `inventory_full.*` | Inventory-full slot estimation |
| `message.*` | Player-facing messages |
| `pack.*` | Material conversion recipes |

## Minimal Recipe Example

```yml
pack:
  IRON_INGOT:
    output: IRON_BLOCK
    ratio_in: 9
    ratio_out: 1
```

This allows 9 iron ingots to pack into 1 iron block.

## Activation

```yml
activation:
  mode: COMMAND
```

Supported modes:

| Mode | Behavior |
| --- | --- |
| `COMMAND` | Players use `/pack` |
| `SMART_PACKER_ITEM` | Players right-click a crafted Smart Packer item |

Changing `activation.mode` requires a server restart so recipe registration and item cleanup can settle cleanly.

See [[Activation Modes|Activation-Modes]] for the full mode guide.

## Crafting Table Requirements

```yml
requirements:
  crafting_table_mode: NEARBY_ONLY
  crafting_table_range: 5
  bypass_crafting_table_for_small_recipes: true
  small_recipe_bypass_max_ratio_in: 4
```

Supported crafting-table modes:

| Mode | Behavior |
| --- | --- |
| `DISABLED` | No crafting table required |
| `INVENTORY_ONLY` | Player must carry a crafting table |
| `NEARBY_ONLY` | Player must be within range of a placed crafting table |
| `INVENTORY_OR_NEARBY` | Either carried or nearby crafting table works |

These settings apply only when `activation.mode` is `COMMAND`.

## Auto-Pack

```yml
auto_pack:
  enabled: false
  default_enabled: false
  cooldown_ticks: 10
  delay_ticks: 2
```

Auto-pack is disabled by default. Players need both `smartpack.use` and `smartpack.auto`, and each player must have their preference enabled through `/pack auto` unless `auto_pack.default_enabled` is changed.

See [[Auto-Pack]] for trigger and mode details.

## Inventory-Full Estimates

```yml
inventory_full:
  include_nearby_pickups: true
  pickup_radius: 2.0
```

When output cannot safely fit, SmartPack leaves the affected conversion unchanged and reports how many extra slots would be needed.

If `include_nearby_pickups` is true, the estimate can include item entities that are already close enough to be picked up. Those nearby items are simulated through the same configured recipe chain before final slot needs are counted.

## Messages

All player-facing messages live under `message.*`.

Common placeholders:

| Placeholder | Meaning |
| --- | --- |
| `[item1]` | Input amount and material |
| `[item2]` | Output amount and material, with leftovers when relevant |
| `[input]` | Total original input count packed |
| `[output]` | Total final item count after packing |
| `[items]` | Combined final material list |
| `[slots]` | Additional inventory slots needed |
| `[range]` | Configured crafting-table range |
| `[mode]` | Invalid configured crafting-table mode |

Minecraft legacy color codes are supported in config messages.

## Validation

```yml
validation:
  warn_if_not_reversible: true
  disable_non_reversible_recipes: false
```

SmartPack can warn when a configured recipe does not appear reversible using the server's loaded crafting recipes. If `disable_non_reversible_recipes` is true, those inputs are skipped at runtime.

This is useful for storage compression servers that want every packed item to be craftable back into its original input.
