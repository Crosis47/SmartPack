# Condense Reforged

Condense Reforged is an enhanced fork of the original MinecraftCondensePlugin by rd156.

It allows players to condense materials directly from their inventory using configurable recipes, with added validation, crafting requirements, and improved feedback.

---

## ✨ Features

- Condense items directly from inventory using `/condense`
- Supports all reversible vanilla compression recipes by default
- Configurable crafting table requirements:
  - Disabled
  - Inventory only
  - Nearby only
  - Inventory or nearby
- Dynamic output messages with leftover materials
- Inventory safety checks (prevents item loss)
- Detailed error feedback for players
- Config validation and auto-upgrade system
- Reload config in-game with `/condense reload`
- Tab completion support for commands

---

## 📦 Example

```text
41 Iron Ingot → 4 Block of Iron + 5 Iron Ingot
```

---

## ⚙️ Commands

| Command | Description |
|--------|------------|
| `/condense` | Condense available materials |
| `/condense reload` | Reload plugin config |

---

## 🔐 Permissions

| Permission | Description | Default |
|-----------|------------|--------|
| `condense.use` | Use `/condense` | true |
| `condense.reload` | Reload config | op |

---

## 🛠️ Configuration

The plugin is fully configurable via `config.yml`.

### Key Features

- Define custom condense recipes
- Enable/disable crafting table requirements
- Control validation behavior
- Customize all messages

---

## ❗ Error Handling Improvements

- Shows full available materials instead of partial consumption
- Displays leftover items in output
- Prevents misleading “nothing to condense” messages
- Provides inventory space failure reasons

### Example

```text
Inventory full: 41 Iron Ingot → 4 Block of Iron + 5 Iron Ingot
Inventory full summary: 3 additional slot(s) would have been needed in total.
```

---

## 🔄 Reloading

You can reload the plugin config without restarting the server:

```text
/condense reload
```

---

## 📜 License

This project is licensed under the GNU General Public License v3.0.

This is a fork of:
https://github.com/rd156/MinecraftCondensePlugin

---

## 📊 Changelog

For a full list of changes and differences from the original plugin, see:

👉 [CHANGELOG.md](./CHANGELOG.md)
