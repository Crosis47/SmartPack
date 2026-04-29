# Auto-Pack

Auto-pack lets permitted players automatically pack configured materials after supported gameplay triggers.

It is disabled by default:

```yml
auto_pack:
  enabled: false
```

## Requirements

Auto-pack runs only when all of these are true:

| Requirement | Details |
| --- | --- |
| Server enabled | `auto_pack.enabled: true` |
| Player preference enabled | `/pack auto` or saved default |
| Permission | Player has `smartpack.use` and `smartpack.auto` |
| Mode allowed | Current activation mode is enabled under `auto_pack.modes.*` |
| Trigger enabled | The matching `auto_pack.triggers.*` setting is true |

## Player Toggle

Players can toggle their saved preference with:

```text
/pack auto
```

In Smart Packer item mode, players can also shift-right-click the Smart Packer item to enable auto-pack if item-mode auto-pack is available.

The preference is stored in `player-exclusions.db`.

## Default Preference

```yml
auto_pack:
  default_enabled: false
```

This controls a player's default preference before they manually change it. The server-wide `auto_pack.enabled` setting and permissions are still required.

## Triggers

```yml
auto_pack:
  triggers:
    pickup: true
    join: false
    crafting_table_place: true
    crafting_table_nearby: true
```

| Trigger | Behavior |
| --- | --- |
| `pickup` | Runs shortly after a player picks up an item entity |
| `join` | Runs shortly after a player joins |
| `crafting_table_place` | Runs after a player places a crafting table, when nearby crafting tables matter |
| `crafting_table_nearby` | Runs when a player enters the configured crafting-table range |

Crafting-table triggers apply only in `COMMAND` mode, only when `requirements.crafting_table_mode` is `NEARBY_ONLY` or `INVENTORY_OR_NEARBY`, and are skipped for `INVENTORY_OR_NEARBY` when the player already has a crafting table in inventory.

## Timing

```yml
auto_pack:
  cooldown_ticks: 10
  delay_ticks: 2
```

| Setting | Purpose |
| --- | --- |
| `cooldown_ticks` | Minimum time between automatic attempts for the same player |
| `delay_ticks` | Delay after a trigger before the pack attempt runs |

The short delay gives the server time to finish moving picked-up items into the player inventory.

## Mode Controls

```yml
auto_pack:
  modes:
    command: true
    smart_packer_item: false
```

In `COMMAND` mode, auto-pack follows the normal crafting-table requirements.

In `SMART_PACKER_ITEM` mode, auto-pack is disabled by default. If enabled, it follows item-mode behavior and can require the player to carry a Smart Packer:

```yml
auto_pack:
  smart_packer_item:
    require_item: true
```

## Feedback

Automatic packing uses actionbar feedback so it stays visible without filling chat.

```yml
auto_pack:
  feedback:
    success_actionbar: true
    inventory_full_actionbar: true
    inventory_full_cooldown_ticks: 100
```

Messages:

```yml
message:
  auto_pack:
    actionbar: "Packed into [items]."
    inventory_full_actionbar: "Pack blocked: need [slots] more slot(s)."
```

Auto-pack uses the same core conversion flow as manual packing, including recipe validation, player exclusions, inventory simulation, multi-tier chaining, and inventory-full checks.
