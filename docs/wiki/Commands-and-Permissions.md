# Commands and Permissions

SmartPack exposes one player-facing command, `/pack`, with subcommands for exclusions, auto-pack, and config reloads.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/pack` | Pack configured materials the player is carrying | `smartpack.use` |
| `/pack exclude` | Open the player exclusion menu | `smartpack.use` |
| `/pack auto` | Toggle automatic packing for the player | `smartpack.use` and `smartpack.auto` |
| `/pack reload` | Reload plugin configuration | `smartpack.reload` |

`/pack`, `/pack exclude`, and `/pack auto` are player-only. `/pack reload` can be run by any command sender with permission.

## Permissions

| Permission | Description | Default |
| --- | --- | --- |
| `smartpack.use` | Allows players to use SmartPack | `true` |
| `smartpack.auto` | Allows automatic packing when `auto_pack.enabled` is true | `false` |
| `smartpack.reload` | Allows reloading the SmartPack config | `op` |

## Command Mode

When `activation.mode` is `COMMAND`, players use `/pack` directly. Crafting-table requirements under `requirements.*` apply in this mode.

Common flow:

```text
/pack
/pack exclude
/pack auto
```

## Smart Packer Item Mode

When `activation.mode` is `SMART_PACKER_ITEM`, players trigger packing by right-clicking a crafted Smart Packer item in their own inventory.

| Interaction | Behavior |
| --- | --- |
| Right-click Smart Packer | Pack now |
| Shift-right-click Smart Packer | Enable auto-pack for that player, if allowed |

In item mode, `/pack` is normally redirected to the item interaction. Admins can allow command use in item mode with:

```yml
activation:
  smart_packer_item:
    allow_command_with_item: true
```

When that setting is true, `/pack` works only if the player is carrying a Smart Packer.

## Reload Behavior

`/pack reload` reloads `config.yml`, refreshes validation, refreshes the Smart Packer recipe, reloads persistent player settings from SQLite, and cleans up leftover Smart Packer items when command mode is active.
