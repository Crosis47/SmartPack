# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

---

## [2.0.0] - 2026-05-05

### Added
- Added a Dev Release GitHub Actions workflow that publishes prerelease jars from development branches.

### Changed
- Updated the build, plugin metadata, release workflow, and README requirements for Paper 26.1.2 and Java 25.
- Declared the SQLite JDBC runtime library in `plugin.yml` so Paper loads the player preference database driver on startup.

---

## [1.5.1] - 2026-05-01

### Changed
- Removed stale activation-mode restart guidance and the unused `message.summary.resume` bundled config alias.

---

## [1.5.0] - 2026-05-01

### Added
- Added chest inventory packing through `/pack chest` in command mode and Smart Packer right-clicks while a chest is open in item mode.
- Added `smartpack.chest` permission and `chest_pack.*` config toggles for chest inventory packing.
- Added optional Smart Packer cooldown mode with live per-second tooltip updates and the `smartpack.cooldown.bypass` permission.
- Added an ActionBar notice when a Smart Packer cooldown finishes.

### Changed
- Disabled auto-pack behavior and removed auto-mode Smart Packer tooltip text while cooldown mode is active.
- Updated the no-materials feedback for chest packing to call out when the target inventory is a chest.
- Reorganized the bundled config into setup-oriented sections while keeping the existing comment style.
- Removed the redundant `auto_pack.modes.*` config layer so `auto_pack.enabled` controls auto-pack in the active activation mode.

---

## [1.4.1] - 2026-04-30

### Added
- Added a GitHub Actions workflow for building and publishing tagged releases.

### Changed
- Updated the release workflow to publish automatically when `pom.xml` version bumps land on `master`.
- Rebranded the plugin to **SmartPack**.
- Renamed the main command from `/condense` to `/pack`.
- Renamed permission nodes from `condense.*` to `smartpack.*`.
- Renamed the crafted activation item to **Smart Packer** and updated the item-mode config paths.
- Renamed the Java package namespace to `crosis47.minecraft.smartpack`.
- Renamed remaining root image/icon assets from `condense*` to `smartpack*`.
- Renamed active internal code identifiers from condense/auto-condense wording to pack/auto-pack wording without legacy migration shims.
- Hid `/pack auto` from tab completion while Smart Packer item mode is active.

### Fixed
- Clarified `/pack auto` feedback in Smart Packer item mode so players are told to enable auto mode through the item.

---

## [1.4.0] - 2026-04-28

### Added
- Added configurable pickup-triggered auto-condensing guarded by the new `condense.auto` permission.
- Added auto-condense triggers for placing crafting tables and entering nearby crafting-table range when command-mode requirements use nearby tables.
- Added actionbar feedback for automatic condense success and inventory-full failures.
- Added auto-condense mode controls for command mode and Condenser item mode.
- Added `/condense auto` so players can toggle their persisted auto-condense preference.
- Added Condenser item shift-right-click support for enabling auto-condense in item mode.
- Added Condenser item tooltip guidance for instant condense and auto-condense interactions.

### Changed
- Avoided nearby crafting-table scans when `INVENTORY_OR_NEARBY` is already satisfied by a crafting table in the player's inventory.
- Reorganized the bundled `config.yml` into clearer sections with more concise comments.
- Moved activation and requirements to the top of the bundled config and changed the default per-player auto-condense preference to disabled.

---

## [1.3.5] - 2026-04-26

### Added
- Added missing vanilla reversible condensables for copper nuggets and resin clumps to the default config.

### Changed
- Streamlined and reordered the default condense recipes by input material family while keeping tiered chains in pass-friendly order.

---

## [1.3.4] - 2026-04-25

### Added
- Added configurable nearby pickup scanning for inventory-full summaries so extra slot estimates can include item entities already within pickup range.

### Changed
- Updated inventory-full summary estimation to simulate configured multi-tier condensation before counting the slots needed for nearby pickup items.

---

## [1.3.3] - 2026-04-24

### Fixed
- Fixed a `/condense` command crash caused by a stale internal method call after the condense display refactor.
- Corrected condense-cycle display aggregation so each original input material is reported once after the full settle window with its final condensed totals.
- Changed the final condense summary to report total original inputs condensed versus the final item count instead of listing intermediate-step totals.

---

## [1.3.2] - 2026-04-24

### Fixed
- Added a short multi-tick settle window to `/condense` so newly picked-up items can join the same condense run after inventory space opens.
- Prevented stale condense snapshots from overwriting inventory changes that happen while a condense attempt is preparing its simulated result.
- Moved exclusion GUI persistence off the main thread and collapsed persistent exclusion saves to one async SQLite write per applied menu session.
- Stopped shading SQLite into the plugin jar so the plugin now relies on Paper's packaged SQLite driver.

---

## [1.3.1] - 2026-04-24

### Added
- Added a green apply button to the exclusion GUI so players can confirm current changes and close the menu without using the cancel control.
- Added a skipped-materials readout after each condense cycle when excluded configured inputs remain in the player's inventory.
- Added apply confirmation when the exclusion GUI is closed normally without using the cancel button.

### Changed
- Renamed the exclusion menu info item to `Exclusion Help` so it reads as guidance instead of an action.

---

## [1.3.0] - 2026-04-24

### Added
- Added `/condense exclude` to open a per-player exclusion GUI for configured input materials.
- Added persistent per-player skipped-material storage in SQLite via `player-exclusions.db`.
- Added paged GUI toggles so excluded materials are marked with a red `X` and ignored by future condense runs.
- Added one-time next-run skips from the GUI via left-click, while right-click manages persistent exclusions.
- Added glowing slot indicators for active exclusions and a bottom-right cancel button that reverts edits made in the open exclusion menu.

---

## [1.2.1] - 2026-04-23

### Added
- Added automatic Condenser cleanup while the plugin is running in `COMMAND` mode.
- Added cleanup coverage for:
  - online players during plugin startup
  - online players during `/condense reload`
  - players joining the server with stale Condensers in saved inventory data

### Fixed
- Removed leftover Condenser items from player inventories after switching away from `CONDENSER_ITEM` mode.
- Prevented stale Condenser items from persisting for players who were offline during a mode change until they rejoined.

### Changed
- Updated documentation to clarify that `COMMAND` mode automatically purges leftover Condenser items from player inventories.

---

## [1.2.0] - 2026-04-23

### Added
- Added a new activation system with two modes:
  - `COMMAND`
  - `CONDENSER_ITEM`
- Added a custom **Condenser** item mode:
  - The item is a named crafting table with an enchantment glint.
  - Players can trigger condensing by right-clicking the Condenser in their inventory.
- Added configurable Condenser recipe support in `config.yml`.
- Added conditional recipe registration so the Condenser crafting recipe only exists on the server while `activation.mode` is `CONDENSER_ITEM`.
- Added `activation.condenser_item.allow_command_with_item` so server admins can choose between:
  - item-only activation
  - both item activation and `/condense` while carrying a Condenser
- Added validation and runtime handling for identifying Condenser items using persistent item data.
- Added a new player-facing message for item mode:
  - `message.info.use_condenser_item`
- Added a new player-facing error when `/condense` is allowed in item mode but the player is not carrying a Condenser:
  - `message.error.condenser_item_required`

### Changed
- Increased the bundled config format version from `1` to `3`.
- Updated the condense flow so command and item activation paths share the same core execution logic.
- Updated `/condense` behavior in `CONDENSER_ITEM` mode:
  - When `allow_command_with_item` is `false`, the command redirects players to use the Condenser item.
  - When `allow_command_with_item` is `true`, the command only works if the player is carrying a Condenser.
- Updated activation-mode behavior so crafting table requirement settings under `requirements.*` only apply in `COMMAND` mode.
- Updated documentation to describe activation modes, Condenser item behavior, and the new configuration options.

### Fixed
- Fixed item mode incorrectly still enforcing crafting table requirements when `allow_command_with_item` was enabled.
- Prevented the Condenser item from being placed as a block.

---

## [1.1.1] - 2026-04-23

### Fixed
- Removed unused `RequirementCheckResult` record and associated methods.
- Eliminated IDE warnings related to dead code.

### Changed
- Minor internal code cleanup following previous refactors.
- No functional or behavioral changes.

---

## [1.1.0] - 2026-04-23

### Added
- Added support for condensing recipes that use **4 or fewer inputs** without requiring a crafting table.
  - Based on the standard 2x2 player crafting grid.
- Added new config options to control small-recipe crafting table bypass:
  - `requirements.bypass_crafting_table_for_small_recipes`
  - `requirements.small_recipe_bypass_max_ratio_in`
- Added contextual player hint:
  - Displays a message when additional materials could be condensed at a crafting table after small recipes are processed.
- Added new configurable message:
  - `message.info.more_available_at_crafting_table`
- Added total input tracking to condense operations.
  - Final summary message now displays both total input items consumed and total output items produced.
  - Example: `Converted 117 items into 13 output items.`

### Changed
- Refactored crafting table requirement logic from a **global command check** to a **per-recipe evaluation system**.
- Mixed inventory behavior improved:
  - Small recipes (≤ threshold) can now condense even if larger recipes are blocked by crafting requirements.
- Improved overall command flow so that valid partial condenses are not blocked by unrelated requirements.
- Updated final summary message format to include both input and output totals.
- Updated config documentation to clarify that the default bypass value of `4` is based on the player crafting grid size.

### Fixed
- Fixed validator rejecting datapack-based recipes that use **tag-based inputs/outputs** (e.g., wool color variants).
- Added validator exception for **variant-based materials** (e.g., colored wool, concrete, glass, etc.) so reversible recipes using tags are no longer incorrectly flagged.
- Prevented valid datapack recipes from being disabled when `disable_non_reversible_recipes` is enabled due to overly strict validation.

---

## [1.0.1] - 2026-04-23

### Added
- Added iterative condensing logic so `/condense` continues processing until no further conversions are possible in the current inventory state.
- Added final inventory-full summary message showing total additional slots required.
- Added clearer inventory-full output showing full input amounts, outputs, and leftover materials.

### Changed
- Inventory-full messages are now delayed until the end of the condense process and only shown if the issue persists.
- Success messages are now displayed correctly during iterative passes.
- Updated item name handling to use modern Paper Adventure translation APIs instead of deprecated methods.
- Updated recipe validation to use modern `RecipeChoice` APIs:
  - Replaced `getIngredientMap()` with `getChoiceMap()`
  - Replaced `getIngredientList()` with `getChoiceList()`
- `/condense` now retries previously failed recipes within the same execution if inventory conditions improve.

### Fixed
- Fixed `nothing_to_condense` appearing after an inventory-full failure during the same command.
- Fixed false-positive inventory-full messages that were later resolved in the same `/condense` execution.
- Fixed condensing requiring multiple command executions due to lack of retry logic.
- Fixed success messages being suppressed after the initial iterative condense refactor.
- Fixed deprecated Paper API usage for shaped and shapeless recipe validation.

---

## [1.0.0] - 2026-04-22

This release is a substantial hard fork of the original **MinecraftCondensePlugin** by **rd156**.

### Added

#### Command improvements
- Added `/condense reload` to reload `config.yml` in-game.
- Added tab completion for the `reload` subcommand.
- Added a dedicated `condense.reload` permission node.

#### Config improvements
- Added a versioned config format with `config-version`.
- Added upgrade-safe config default merging so new keys can be added without overwriting customized settings.
- Expanded the config with documentation and cleaner message handling.

#### Condensing behavior improvements
- Added leftover-aware result formatting.
  - Example: `11 Iron Ingot → 1 Block of Iron + 2 Iron Ingot`
- Added an inventory-full summary showing total additional slots needed after a single `/condense` attempt.

#### Crafting table requirement system
- Added configurable crafting table requirement modes:
  - `DISABLED`
  - `INVENTORY_ONLY`
  - `NEARBY_ONLY`
  - `INVENTORY_OR_NEARBY`
- Added specific failure messages for each requirement mode.

#### Validation and safety improvements
- Added startup validation for:
  - invalid input materials
  - invalid output materials
  - invalid ratios
  - invalid crafting table mode values
  - malformed condense entries
- Added optional validation for recipe reversibility using currently loaded server recipes.
- Added optional strict mode to disable non-reversible configured recipes at runtime.
- Continued using safe inventory simulation before applying any item mutations.

#### Default content changes
- Reworked the default configuration to focus on reversible vanilla condense recipes.
- Expanded the default documentation and plugin messages.

#### Documentation improvements
- Rewrote the project README to reflect current fork behavior instead of the minimal original README.

---

### Changed

#### Fork / identity changes
- Renamed the plugin branding to **Condense Reforged**.
- Renamed the Java package namespace from `rd156.minecraft.condense` to `crosis47.minecraft.condense`.
- Updated the plugin entry point and current code structure to match the new namespace.

#### Platform and build modernization
- Updated the project to build against **Paper API 1.21.11-R0.1-SNAPSHOT**.
- Kept compiled target compatibility at **Java 21** for Paper 1.21.x servers.
- Modernized project structure while preserving the original `/condense` concept.

#### Behavior improvements
- Fixed output handling so configured `ratio_out` values are respected correctly.
- Improved player-facing output to use in-game material display names instead of raw enum names.
- Improved inventory-full behavior to better explain failed conversions.

---

### Fixed
- Prevented `nothing_to_condense` from incorrectly appearing after an inventory-full failure.
- Fixed multiple edge cases around invalid materials and malformed config entries.
- Fixed potential issues where invalid recipes could cause silent failures.

---

## Upstream baseline

The original upstream project was a small Bukkit-style plugin with:
- A minimal README
- A simple `/condense` command
- A small Maven project
- A package rooted under `rd156.minecraft.condense`
