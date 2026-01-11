# CraftGUI

CraftGUI is a Minecraft pluign that provides a highly customizeable GUI interface for crafting and item transformation.

This plugin allows server administrators to define craftable items and required materials via config files.
It also supports MythicMobs items.

# Environment
This plugin depends on MythicMobs (v4.12.0), so it is compatible with servers that run this plugin version.

## Commands

| Command                                                    | Description                                                             |
|------------------------------------------------------------|-------------------------------------------------------------------------|
| `/craftgui`                                                | Opens the CraftGUI                                                      |
| `/craftgui page=<page>`                                    | Opens the specified page.                                               |
| `/craftgui craft <id> <amount>`                            | Craft by specifying the item ID in the command                          |
| `/craftgui register id=<recipeId> page=<page> slot=<slot>` | Register an item. <br>All fields are optional except for the recipe ID. |
| `/craftgui edit`                                           | Edit recipes.                                                           |
| `/craftgui errors`                                         | Show error logs.                                                        |
| `/craftgui config reload`                                  | Reloads the config.yml file                                             |
| `/craftgui config reload --external`                       | Reloads configuration from the external URL defined in `configUrl`      |

Aliases:
- `/rgui`
- `//rgui`
- `/ragui`
- `//ragui`
- `//craft`

## Permissions

| Permission | Description |
|-------------|-------------|
| `craftgui.admin` | Grants full administrative permissions for CraftGUI |


## Configuration Example

```yaml
# CraftGUI Configuration
configVersion: 1.0

# URL for reloading configuration from an external file
configUrl: "URL"

# Language JSON download source
jsonUrl: "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.15.2/assets/minecraft/lang/"
# Supported languages
languages:
  - "ja_jp"
  - "en_us"

# GUI items configuration
Items:
  page1:
    0:
      enabled: true
      id: "UniqueId"
      resultItems:
        - mmid: "SpecialStone"
          amount: 1
      requiredItems:
        - material: STONE
          amount: 4
        - mmid: "BigPowder"
          amount: 2

# Lore templates
Lores:
  CommonLore:
    - '&fLeft-click: Transform once'
    - '&fRight-click: Transform as many as possible'
```

---

## License

This project is licensed under the [GNU General Public License](LICENSE).
