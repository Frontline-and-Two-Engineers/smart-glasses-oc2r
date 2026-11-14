# Smart Glasses OC2R — Документация

Аддон для **OpenComputers II: Modern (OC2R)** на **NeoForge 1.21.1**, вдохновлённый *OpenPeripherals Terminal Glasses* (1.7.10), с поддержкой 2D HUD, 3D AR-разметки в мире, кликабельных виджетов, чат-команд и сканера взгляда.

## Содержание

1. **[Архитектура кода мода](architecture.md)** — устройство проекта, пакеты, сеть, интеграция с OC2R, рендер, миксин, энергия и Curios.
2. **[Справочник API (`glasses_bridge`)](api-reference.md)** — все RPC-методы, события, координаты, якоря и цвета.
3. **[Примеры программ для OC2R](examples.md)** — готовые скрипты на Lua и Python (HUD, кликабельные кнопки, чат-бот `$$`, 3D AR-сканер `G`, телеметрия игрока и графики).

## Быстрый старт в игре

1. Скрафтите **Мост умных очков** (`smartglasses:glasses_bridge`) и **Умные очки** (`smartglasses:smart_glasses`).
2. Подключите мост к компьютеру OC2R через **Bus Cable**.
3. Нажмите **ПКМ** очками по мосту для привязки и наденьте очки в слот шлема (или слот `head` в Curios).
4. В Linux на компьютере OC2R:
   ```sh
   export LUA_PATH="/mnt/builtin/lib/lua/?.lua"
   lsdev.lua
   ```
   В списке устройств появится `glasses_bridge`.
