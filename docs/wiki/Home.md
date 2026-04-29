<p align="center">
  <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/branding/smartpack-hero.png" alt="SmartPack branding banner" width="900">
</p>

# SmartPack Wiki

**Pack more. Carry less. Play smarter.**

SmartPack is a Paper plugin for fast, safe Minecraft inventory compression with `/pack`. It packs configured materials directly from a player's inventory, turning loose resources into compact storage blocks without manual crafting loops.

SmartPack is designed for storage-style conversions such as nuggets to ingots, ingots to blocks, redstone to redstone blocks, resin clumps to resin blocks, wheat to hay blocks, and other server-configured recipes.

<p align="center">
  <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/branding/smartpack-features.png" alt="SmartPack feature summary" width="900">
</p>

## Quick Links

- [[Installation]]
- [[Commands and Permissions|Commands-and-Permissions]]
- [[Configuration]]
- [[Activation Modes|Activation-Modes]]
- [[Auto-Pack]]
- [[Player Exclusions|Player-Exclusions]]
- [[Recipe Configuration|Recipe-Configuration]]
- [[Crafting Table Requirements|Crafting-Table-Requirements]]
- [[Safety and Validation|Safety-and-Validation]]
- [[Troubleshooting]]
- [[Branding and Screenshots|Branding-and-Screenshots]]

## At A Glance

| Area | Details |
| --- | --- |
| Platform | Paper |
| Minecraft API | `1.21.11` |
| Java | `21` |
| Main command | `/pack` |
| Player permission | `smartpack.use` |
| Auto-pack permission | `smartpack.auto` |
| Admin permission | `smartpack.reload` |
| Config file | `plugins/SmartPack/config.yml` |
| Player data | `plugins/SmartPack/player-exclusions.db` |

## What SmartPack Does

- Packs materials using recipes defined in `config.yml`.
- Works directly from the player's inventory.
- Supports manual command activation with `/pack`.
- Supports an optional crafted Smart Packer item activation mode.
- Supports trigger-based auto-pack with actionbar feedback.
- Lets players skip materials through `/pack exclude`.
- Simulates inventory changes before applying them to prevent item loss.
- Reports inventory-full failures with slot estimates.
- Supports configurable crafting-table requirements.
- Warns about non-reversible recipes and can disable them automatically.
- Reloads configuration in game with `/pack reload`.

## Visual Tour

<table>
  <tr>
    <td width="50%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/screenshots/1.png" alt="Iron ingots packed into iron blocks with chat summary" width="100%">
      <br><strong>Fast inventory packing</strong><br>
      Large stacks compress directly from the player inventory, with chat summarizing the result.
    </td>
    <td width="50%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/screenshots/2.png" alt="Gold nuggets packed through ingots into gold blocks" width="100%">
      <br><strong>Multi-tier compression</strong><br>
      Tiered recipes can continue in one run, such as nuggets turning into blocks with leftovers preserved.
    </td>
  </tr>
  <tr>
    <td width="50%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/screenshots/3.png" alt="SmartPack Exclusions menu showing configured inputs" width="100%">
      <br><strong>Player exclusions</strong><br>
      Players can skip specific configured inputs for one run or persistently.
    </td>
    <td width="50%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/screenshots/10.png" alt="Inventory full warning after attempting to pack redstone" width="100%">
      <br><strong>Inventory safety</strong><br>
      Blocked conversions leave inventories unchanged and explain how many extra slots are needed.
    </td>
  </tr>
</table>

## Project Background

SmartPack is a fork of the original MinecraftCondensePlugin by `rd156`. The current fork reworks the plugin around the SmartPack name, `/pack` command, `smartpack.*` permissions, player exclusions, optional Smart Packer item mode, auto-pack, and a safer configurable packing flow.

## Source

- Repository: https://github.com/Crosis47/SmartPack
- License: GNU General Public License v3.0
