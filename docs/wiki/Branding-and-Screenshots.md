# Branding and Screenshots

SmartPack includes ready-to-use branding and screenshot assets in the repository under `docs/branding` and `docs/screenshots`.

Use raw GitHub URLs when embedding these images in GitHub Wiki pages.

Base URL:

```text
https://raw.githubusercontent.com/Crosis47/SmartPack/master/
```

Example:

```html
<img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/branding/smartpack-hero.png" alt="SmartPack branding banner" width="900">
```

## Branding Assets

<table>
  <tr>
    <td width="50%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/branding/smartpack-hero.png" alt="SmartPack branding banner" width="100%">
      <br><code>docs/branding/smartpack-hero.png</code>
    </td>
    <td width="50%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/branding/smartpack-features.png" alt="SmartPack feature summary" width="100%">
      <br><code>docs/branding/smartpack-features.png</code>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/branding/smartpack-wordmark.png" alt="SmartPack wordmark" width="100%">
      <br><code>docs/branding/smartpack-wordmark.png</code>
    </td>
    <td width="50%">
      <img src="https://raw.githubusercontent.com/Crosis47/SmartPack/master/docs/branding/smartpack-icon-square.png" alt="SmartPack square icon" width="180">
      <br><code>docs/branding/smartpack-icon-square.png</code>
    </td>
  </tr>
</table>

Additional assets:

| File | Suggested Use |
| --- | --- |
| `docs/branding/smartpack-branding.png` | Full branding reference |
| `docs/branding/smartpack-mini-icon.png` | Sidebar or small page accents |

## Screenshot Catalog

| File | Suggested Wiki Use |
| --- | --- |
| `docs/screenshots/1.png` | Basic iron ingot to block packing |
| `docs/screenshots/2.png` | Multi-tier gold nugget to block packing |
| `docs/screenshots/3.png` | Exclusion menu overview |
| `docs/screenshots/4-1.png` | Normal exclusion menu item state |
| `docs/screenshots/4-2.png` | One-time next-run exclusion state |
| `docs/screenshots/4-3.png` | Persistent exclusion state |
| `docs/screenshots/5.png` | Exclusion help tooltip |
| `docs/screenshots/6.png` | Apply and cancel controls |
| `docs/screenshots/7.png` | Skipped excluded material feedback |
| `docs/screenshots/8.png` | Crafting table requirement failure |
| `docs/screenshots/9.png` | Nearby crafting table success |
| `docs/screenshots/10.png` | Inventory-full warning |
| `docs/screenshots/11.png` | Smart Packer crafting recipe |
| `docs/screenshots/12.png` | Smart Packer activation in inventory |

## Recommended Image Placement

| Page | Assets |
| --- | --- |
| `Home` | `smartpack-hero.png`, `smartpack-features.png`, screenshots `1`, `2`, `3`, `10` |
| `Player Exclusions` | screenshots `3`, `4-1`, `4-2`, `4-3`, `5`, `6`, `7` |
| `Activation Modes` | screenshots `11`, `12` |
| `Crafting Table Requirements` | screenshots `8`, `9` |
| `Safety and Validation` | screenshot `10` |

## Alt Text Pattern

Use descriptive alt text that explains the UI state, not just the file name.

Good:

```html
<img src="..." alt="SmartPack Exclusions menu showing configured inputs">
```

Avoid:

```html
<img src="..." alt="screenshot 3">
```
