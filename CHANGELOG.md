# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/).

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

#### Config and migration improvements
- Added a versioned config format with `config-version`.
- Added upgrade-safe config default merging so new keys can be added without overwriting customized settings.
- Added migration support for older crafting-table boolean settings.
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