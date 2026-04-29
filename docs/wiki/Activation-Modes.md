# Activation Modes

SmartPack supports two activation modes:

| Mode | Primary Player Action |
| --- | --- |
| `COMMAND` | Run `/pack` |
| `SMART_PACKER_ITEM` | Right-click a crafted Smart Packer item |

Set the mode in `config.yml`:

```yml
activation:
  mode: COMMAND
```

Changing activation mode requires a server restart.

## Command Mode

`COMMAND` is the default mode. Players pack materials with:

```text
/pack
```

In command mode:

- `requirements.*` crafting-table settings are enforced.
- Auto-pack follows command-mode crafting-table requirements.
- Smart Packer items are cleaned up from online players on startup, reload, and player join.

## Smart Packer Item Mode

`SMART_PACKER_ITEM` registers a custom crafted item named **Smart Packer**. Players right-click it from their inventory to pack materials.

<table>
  <tr>
    <td width="50%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/screenshots/11.png" alt="Smart Packer crafting recipe in the crafting table interface" width="100%">
      <br><strong>Crafted Smart Packer item</strong><br>
      The configured recipe creates a named crafting table item with a glint.
    </td>
    <td width="50%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/screenshots/12.png" alt="Smart Packer item in inventory after packing materials" width="100%">
      <br><strong>Inventory activation</strong><br>
      Right-clicking the Smart Packer runs the same core packing flow.
    </td>
  </tr>
</table>

In Smart Packer item mode:

- `requirements.*` crafting-table checks are ignored.
- The Smart Packer item cannot be placed as a block.
- The Smart Packer recipe is registered only while item mode is active.
- The item has custom persistent data so ordinary crafting tables are not treated as Smart Packers.

## Item Interactions

| Interaction | Behavior |
| --- | --- |
| Right-click Smart Packer in player inventory | Pack now |
| Shift-right-click Smart Packer in player inventory | Enable auto-pack for that player, if available |

The Smart Packer tooltip lists both interactions.

## Allowing `/pack` In Item Mode

By default, item mode requires item activation. Server owners can also allow `/pack` while the player is carrying a Smart Packer:

```yml
activation:
  smart_packer_item:
    allow_command_with_item: true
```

When enabled, `/pack` still requires the player to have a Smart Packer in their inventory.

## Smart Packer Recipe

The default recipe is:

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

Recipe rows must be 1 to 3 characters wide, all rows must have the same width, and every non-space key in the shape must have a valid item material under `ingredients`.
