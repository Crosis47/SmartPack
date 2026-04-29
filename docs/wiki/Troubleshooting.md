# Troubleshooting

This page covers common SmartPack setup and player support issues.

## `/pack` Says Nothing Can Be Packed

Check:

- The player has materials listed under `pack:`.
- The material is not excluded in `/pack exclude`.
- The recipe was not disabled by reversible validation.
- The player satisfies the crafting-table requirement for that recipe.
- The configured input and output material names are valid.

## Player Lacks Permission

Permissions:

| Action | Required Permission |
| --- | --- |
| Use `/pack` | `smartpack.use` |
| Use `/pack auto` | `smartpack.use` and `smartpack.auto` |
| Reload config | `smartpack.reload` |

`smartpack.use` defaults to `true`, but permission plugins can override it.

## Crafting Table Required Message Appears

Check:

```yml
requirements:
  crafting_table_mode: NEARBY_ONLY
  crafting_table_range: 5
```

Depending on the mode, the player may need a crafting table in inventory, a placed crafting table nearby, or either one.

If only small recipes are expected to run without a table, check:

```yml
requirements:
  bypass_crafting_table_for_small_recipes: true
  small_recipe_bypass_max_ratio_in: 4
```

## Inventory Full Warning Appears

SmartPack leaves blocked conversions unchanged when output would not fit.

Ask the player to free the number of slots shown in the summary, or adjust:

```yml
inventory_full:
  include_nearby_pickups: true
  pickup_radius: 2.0
```

Nearby pickup estimates can make slot requirements look larger when loose items are already close to the player.

## Auto-Pack Does Not Run

Check all of these:

- `auto_pack.enabled` is true.
- The player has `smartpack.use`.
- The player has `smartpack.auto`.
- The player has enabled their preference with `/pack auto`.
- The current activation mode is allowed under `auto_pack.modes.*`.
- The relevant trigger is enabled under `auto_pack.triggers.*`.
- The player is not still inside `auto_pack.cooldown_ticks`.

For Smart Packer item mode, also check:

```yml
auto_pack:
  modes:
    smart_packer_item: true
  smart_packer_item:
    require_item: true
```

If `require_item` is true, the player must carry a Smart Packer.

## Smart Packer Recipe Does Not Exist

The Smart Packer recipe is registered only when:

```yml
activation:
  mode: SMART_PACKER_ITEM
```

After changing activation mode, restart the server.

Also check that every recipe shape key has a valid material:

```yml
activation:
  smart_packer_item:
    recipe:
      shape:
        - "IRI"
        - "RCR"
        - "IRI"
      ingredients:
        I: IRON_INGOT
        R: REDSTONE
        C: CRAFTING_TABLE
```

## `/pack` Is Blocked In Smart Packer Item Mode

This is expected when:

```yml
activation:
  smart_packer_item:
    allow_command_with_item: false
```

Set it to true if players should be able to use `/pack` while carrying a Smart Packer.

## Config Changes Did Not Apply

Run:

```text
/pack reload
```

If the change affects `activation.mode`, restart the server.

Watch the server console for SmartPack config warnings after reload or startup.

## Console Warns About Non-Reversible Recipes

SmartPack could not confirm that a configured pack recipe can be crafted back using the server's loaded recipes.

Options:

- Leave the warning enabled and allow the recipe.
- Add or fix the reverse recipe through server recipes or datapacks.
- Set `validation.disable_non_reversible_recipes: true` to block those inputs.
- Remove the recipe from `pack:`.
