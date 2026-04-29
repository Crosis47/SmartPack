# Player Exclusions

Players can use `/pack exclude` to choose configured input materials that SmartPack should skip.

<p align="center">
  <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/screenshots/3.png" alt="SmartPack Exclusions menu showing configured inputs" width="560">
</p>

## Menu Controls

| Action | Behavior |
| --- | --- |
| Left-click a material | Toggle a one-time next-run skip |
| Right-click a material | Toggle a persistent skip |
| Green apply button | Save current edits and close |
| Close inventory normally | Save current edits |
| Red cancel button | Revert all edits made during the current menu session |

Glowing slots indicate that an exclusion is currently active for that material.

## One-Time Exclusions

One-time exclusions apply to the next pack cycle only. After that cycle finishes, SmartPack clears the player's next-run exclusions.

This also applies to auto-pack: if the next pack cycle happens automatically, the one-time skip is consumed by that automatic run.

## Persistent Exclusions

Persistent exclusions are saved in:

```text
plugins/SmartPack/player-exclusions.db
```

They remain active across sessions until the player toggles the material again in `/pack exclude`.

## Exclusion States

<table>
  <tr>
    <td width="33%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/screenshots/4-1.png" alt="Tooltip for a normal non-excluded wheat entry" width="100%">
      <br><strong>Normal input</strong><br>
      The material is eligible for packing.
    </td>
    <td width="33%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/screenshots/4-2.png" alt="Tooltip for a temporary next-run exclusion" width="100%">
      <br><strong>Next-run skip</strong><br>
      The material is skipped once.
    </td>
    <td width="33%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/screenshots/4-3.png" alt="Tooltip for a persistent copper ingot exclusion" width="100%">
      <br><strong>Persistent skip</strong><br>
      The material remains skipped.
    </td>
  </tr>
</table>

## Help And Controls

<table>
  <tr>
    <td width="50%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/screenshots/5.png" alt="Exclusion Help tooltip explaining menu controls" width="100%">
      <br><strong>Built-in help</strong><br>
      The info item explains left-click, right-click, glowing slots, apply, and cancel behavior.
    </td>
    <td width="50%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/screenshots/6.png" alt="Cancel Changes tooltip in the exclusion menu" width="100%">
      <br><strong>Apply and cancel</strong><br>
      Bottom-row controls let players keep or discard edits from the current menu session.
    </td>
  </tr>
</table>

## Skipped Materials Message

After a pack cycle, SmartPack can report configured inputs that were skipped and remain in the player's inventory.

```yml
message:
  info:
    skipped_excluded_materials: "Skipped excluded materials in your inventory: [items]."
```

<p align="center">
  <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/screenshots/7.png" alt="Excluded iron nuggets skipped while gold nuggets pack" width="760">
</p>

## Admin Notes

- Exclusions are evaluated against configured input materials.
- Disabled or invalid pack recipes are not useful exclusion targets.
- Persistent exclusions and auto-pack preferences share the same SQLite database file.
- Exclusions are respected by both manual `/pack` and automatic packing.
