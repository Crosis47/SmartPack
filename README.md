<p align="center">
  <img src="docs/branding/smartpack-hero.png" alt="SmartPack branding banner" width="900">
</p>

<h1 align="center">SmartPack</h1>

<p align="center">
  <strong>Pack more. Carry less. Play smarter.</strong>
  <br>
  A Paper plugin for fast, safe Minecraft inventory compression with <code>/pack</code>.
</p>

<p align="center">
  <img src="docs/branding/smartpack-features.png" alt="SmartPack feature summary" width="900">
</p>

SmartPack packs configured inventory materials into compact storage forms, such as nuggets to ingots, ingots to blocks, redstone to redstone blocks, and other server-configured recipes. It is built for survival servers that want quick inventory cleanup with sensible safety checks.

## Highlights

- Safe inventory simulation before item changes are applied.
- Command, Smart Packer item, and optional auto-pack activation.
- Optional Smart Packer cooldown mode with live tooltip updates and a ready ActionBar notice.
- Chest inventory packing through `/pack chest` or the Smart Packer item.
- Per-player material exclusions through `/pack exclude`.
- Configurable crafting-table requirements.
- Reversible-recipe warnings and optional strict disabling.
- Inventory-full feedback with extra-slot estimates.

## Documentation

The full setup and usage guide lives in the GitHub Wiki:

- [SmartPack Wiki](https://github.com/Crosis47/SmartPack/wiki)
- [Installation](https://github.com/Crosis47/SmartPack/wiki/Installation)
- [Commands and Permissions](https://github.com/Crosis47/SmartPack/wiki/Commands-and-Permissions)
- [Configuration](https://github.com/Crosis47/SmartPack/wiki/Configuration)
- [Chest Packing](https://github.com/Crosis47/SmartPack/wiki/Chest-Packing)
- [Recipe Configuration](https://github.com/Crosis47/SmartPack/wiki/Recipe-Configuration)
- [Troubleshooting](https://github.com/Crosis47/SmartPack/wiki/Troubleshooting)

## Requirements

- Java 25
- Paper 26.1.2
- Maven 3.x, when building from source

On first startup, Paper downloads SmartPack's SQLite JDBC runtime library unless
that library is already cached on the server.

## Build

```bash
mvn clean package
```

The compiled jar is created in `target/` as `smartpack-<version>.jar`.

## Release

GitHub Actions publishes releases automatically when a push to `master` changes
the Maven project version in `pom.xml`. Update `pom.xml` and `CHANGELOG.md`,
commit the version bump, and push:

```bash
git push origin master
```

The workflow builds with Java 25, verifies that the tag matches the Maven
version, creates `v<version>` after the build passes, and uploads
`target/smartpack-<version>.jar` to the GitHub release.

Releases can still be created by pushing a version tag directly:

```bash
git tag v2.0.0
git push origin v2.0.0
```

The workflow can also be run manually from the Actions tab with either `X.Y.Z`
or `vX.Y.Z`. Manual runs use an existing matching tag when one is present; when
the tag does not exist yet, the workflow creates it from the selected branch
after the build passes.

### Development Releases

Development branch pushes publish GitHub prereleases through the Dev Release
workflow. It runs for `dev`, `dev/**`, `feature/**`, `release/**`, and `26.*`
branches, including compatibility branches such as `26.1.2`.

Each dev build creates a unique prerelease tag like
`dev-<branch>-<run>.<attempt>` and uploads a clearly named jar like
`SmartPack-<version>-dev.<run>.<attempt>-<branch>-<sha>.jar`. These builds are
for testing and do not replace the stable `master` version-bump release flow.

## Install

1. Place the jar in your server's `plugins/` directory.
2. Start the server once to generate `plugins/SmartPack/config.yml`.
3. Adjust the config.
4. Run `/pack reload`.

## Commands

| Command | Description |
| --- | --- |
| `/pack` | Pack configured materials |
| `/pack chest` | Pack the chest you are looking at |
| `/pack exclude` | Choose materials to skip |
| `/pack auto` | Toggle automatic packing |
| `/pack reload` | Reload the config |

## License

SmartPack is licensed under the GNU General Public License v3.0. See [License.md](License.md).

## Credits

SmartPack is a fork of the original [MinecraftCondensePlugin](https://github.com/rd156/MinecraftCondensePlugin) by `rd156`.
