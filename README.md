# MonayKits

Standalone kit storage and claiming plugin for MonaySMP.

## Target
- Paper 26.2 (API build 134)
- Java 25
- Java Edition: Paper Dialog API menu
- Bedrock Edition: chest-menu fallback when Floodgate is installed

## Build
Push to `main` or run the **Build MonayKits** workflow manually from the GitHub Actions tab. Download the `MonayKits` artifact from a successful workflow run. The JAR is produced at `build/libs/MonayKits-1.0.0.jar`.

## Install
1. Put the JAR in the Paper server's `plugins` folder.
2. Restart the server.
3. Edit `plugins/MonayKits/config.yml` to set kit permissions and cooldowns.
4. Give staff `monaykits.admin`.

## Commands
- `/monaykits` — open the menu
- `/monaykits set <kit>` — save your current inventory and armor as a kit
- `/monaykits claim <kit>` — claim a kit
- `/monaykits preview <kit>` — list a kit's contents in chat
- `/monaykits reload` — reload config

Valid kit keys: `starter`, `og`, `comedian`, `booster`, `monayplus`.

## Permissions
- `monaykits.use` — open menu (default true)
- `monaykits.admin` — configure kits and bypass kit permission/cooldown checks (default op)
- `monaykits.kit.starter` — Starter kit (default true)
- `monaykits.kit.og`
- `monaykits.kit.comedian`
- `monaykits.kit.booster`
- `monaykits.kit.monayplus`

## Notes
Kits start empty intentionally. Hold the items you want to save in your inventory, then run `/monaykits set starter` (or another kit key). Kit contents and claim timestamps are saved to disk. Install Floodgate for the Bedrock fallback detection; without Floodgate, the plugin uses the native dialog path and falls back to an inventory menu if the dialog API throws an error.
