# KeyallMC

A configurable key distribution plugin for Paper servers.

## Requirements
- Paper (or compatible) 1.21.x through 26.3
- Java 21+ (runtime requirements are determined by the server version)

## Commands
- `/keyall <key> [amount]` — executes the configured reward command for every online player.
- `/keyall reload` — reloads the plugin configuration files without restarting.

Permission: `keyallmc.admin` (default: OP).

## Configuration
- `config.yml`: only plugin version and plugin enabled state.
- `settings/keyall.yml`: reward command and broadcast announcement.
- `messages.yml`: message text.
- `settings/message-settings.yml`: message delivery mode (ACTION_BAR, TITLE, CHAT).

Reward command placeholders: `%player%`, `%key%`, `%amount%`.

Default reward command: `crate givekey %player% %key% %amount%`.

Invalid or missing config values are handled with fallback defaults where practical. The `/keyall reload` command reloads settings safely.
