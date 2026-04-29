# Crafting Table Requirements

SmartPack can require players to have access to a crafting table before larger recipes run.

These settings apply only when:

```yml
activation:
  mode: COMMAND
```

In `SMART_PACKER_ITEM` mode, the Smart Packer item itself is the activation requirement, so `requirements.*` crafting-table checks are ignored.

## Modes

```yml
requirements:
  crafting_table_mode: NEARBY_ONLY
```

| Mode | Behavior |
| --- | --- |
| `DISABLED` | No crafting table required |
| `INVENTORY_ONLY` | Player must carry a crafting table |
| `NEARBY_ONLY` | Player must be within range of a placed crafting table |
| `INVENTORY_OR_NEARBY` | Either carried or nearby crafting table works |

## Range

```yml
requirements:
  crafting_table_range: 5
```

`crafting_table_range` controls the radius used for nearby placed crafting tables.

<table>
  <tr>
    <td width="50%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/screenshots/9.png" alt="Packing near a crafting table succeeds" width="100%">
      <br><strong>Nearby crafting table accepted</strong><br>
      Standing within range lets recipes run when nearby mode is enabled.
    </td>
    <td width="50%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/screenshots/8.png" alt="Crafting table required error message" width="100%">
      <br><strong>Out-of-range protection</strong><br>
      Players get a clear message when a recipe requires a crafting table and none is close enough.
    </td>
  </tr>
</table>

## Small Recipe Bypass

Small recipes can ignore the crafting-table requirement:

```yml
requirements:
  bypass_crafting_table_for_small_recipes: true
  small_recipe_bypass_max_ratio_in: 4
```

The default maximum is `4` because the vanilla player crafting grid has four slots.

Example: a 4-input custom storage recipe can still run from the player inventory, while a 9-input block recipe waits for crafting-table access.

## Partial Packing

Crafting-table checks are evaluated per recipe. This means a player can still pack eligible small recipes even if larger recipes are blocked by crafting-table requirements.

If small recipes run but larger recipes are still available at a crafting table, SmartPack can show:

```yml
message:
  info:
    more_available_at_crafting_table: "More materials can be packed at a crafting table."
```

## Auto-Pack And Nearby Tables

Auto-pack crafting-table triggers use the same requirement settings.

| Trigger | Applies When |
| --- | --- |
| `crafting_table_place` | A player places a crafting table and nearby tables can satisfy requirements |
| `crafting_table_nearby` | A player walks into range of a crafting table |

These triggers run only in `COMMAND` mode and only for `NEARBY_ONLY` or `INVENTORY_OR_NEARBY`.

When mode is `INVENTORY_OR_NEARBY` and the player already has a crafting table in inventory, SmartPack skips nearby-table scans.
