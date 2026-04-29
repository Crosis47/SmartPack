# Safety and Validation

SmartPack is built around safe inventory changes. It simulates pack results before mutating the player's real inventory, then applies only conversions that can safely fit.

## Inventory Simulation

When a player runs `/pack`, SmartPack:

1. Loads configured recipes.
2. Checks recipe-specific crafting-table requirements.
3. Counts matching materials in the player's inventory.
4. Simulates removing inputs and adding outputs.
5. Applies successful conversions.
6. Repeats until no more configured conversions can run.

If an output would not fit, that conversion is left unchanged.

## Inventory-Full Handling

<p align="center">
  <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/screenshots/10.png" alt="Inventory full warning after attempting to pack redstone" width="820">
</p>

Manual packing can send an inventory-full line for blocked conversions and a final summary:

```yml
message:
  error:
    inventory_full: "Inventory full: [item1] -> [item2]"
    inventory_full_summary: "Inventory full summary: [slots] additional slot(s) needed."
```

Automatic packing can send an actionbar warning:

```yml
message:
  auto_pack:
    inventory_full_actionbar: "Pack blocked: need [slots] more slot(s)."
```

## Nearby Pickup Slot Estimates

```yml
inventory_full:
  include_nearby_pickups: true
  pickup_radius: 2.0
```

When enabled, SmartPack can include nearby item entities in the extra-slot estimate. It simulates those nearby items through configured recipes, exclusions, crafting-table rules, and multi-tier chains before counting the final slot need.

This makes inventory-full messages more useful when a player is standing near items they are about to pick up.

## Reversible Recipe Validation

```yml
validation:
  warn_if_not_reversible: true
  disable_non_reversible_recipes: false
```

SmartPack checks configured recipes against the server's loaded crafting recipes.

| Setting | Behavior |
| --- | --- |
| `warn_if_not_reversible` | Logs warnings for recipes that do not appear reversible |
| `disable_non_reversible_recipes` | Prevents non-reversible recipes from running |

Variant families such as colored blocks can be accepted when datapack or tag-based recipes make exact reverse matching too strict.

## Invalid Config Entries

SmartPack logs warnings and skips unsafe entries when it sees:

- Missing `pack:` section.
- Invalid input material names.
- Invalid output material names.
- Non-item materials.
- `ratio_in` values less than or equal to 0.
- `ratio_out` values less than or equal to 0.
- Invalid activation or crafting-table mode names.

## Player Data Safety

Persistent per-player settings are stored in SQLite:

```text
plugins/SmartPack/player-exclusions.db
```

The database stores:

- Persistent material exclusions.
- Saved auto-pack preferences.

Back up this file if you want to preserve player preferences while moving servers.
