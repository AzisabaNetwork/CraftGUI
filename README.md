# CraftGUI

CraftGUI is a Minecraft pluign that provides a highly customizeable GUI interface for crafting and item transformation.

This plugin allows server administrators to define craftable items and required materials via config files.
It also supports MythicMobs items.

# Environment
This plugin depends on MythicMobs (v4.12.0), so it is compatible with servers that run this plugin version.

## Commands

| Command | Description |
|----------|-------------|
| `/craftgui` | Opens the CraftGUI |
| `/craftgui craft <id> <amount>` | Craft by specifying the item ID in the command |
| `/craftgui config reload` | Reloads the config.yml file |
| `/craftgui config reload --external` | Reloads configuration from the external URL defined in `configUrl` |

Aliases:
- `rgui`
- `/rgui`
- `ragui`
- `/ragui`
- `/craft`

## Permissions

| Permission | Description |
|-------------|-------------|
| `craftgui.admin` | Grants full administrative permissions for CraftGUI |


## Configuration Example

```yaml
# CraftGUI Extension Configuration
configVersion: 1.0

# URL for reloading configuration from an external file
ConfigURL: "URL"

# Language JSON download source
JsonURL: "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.15.2/assets/minecraft/lang/"
# Supported languages
languages:
  - "ja_jp"
  - "en_us"

# GUI items configuration
Items:
  page1:
    slot0:
      enabled: true
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

---

### Included Libraries

This project bundles the following third-party library:

- **[Item-NBT-API](https://github.com/tr7zw/Item-NBT-API)**  
  - Copyright (c) tr7zw  
  - Licensed under the [MIT License](https://opensource.org/licenses/MIT)

The MIT license text for the bundled library is reproduced below as required.

---

### Item-NBT-API License (MIT)

```
MIT License

Copyright (c) tr7zw

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

---

## Notes

- This plugin includes shaded code from **Item-NBT-API**, which is licensed under the MIT license.
- License text and attribution are included as required by the MIT license.
