# Справочник API (`glasses_bridge`)

Устройство подключается к компьютеру OC2R по **Bus Cable** и определяется под именем **`glasses_bridge`**.

```lua
local devices = require("devices")
local g = assert(devices:find("glasses_bridge"))
```

---

## Базовые понятия

### Виртуальные координаты (2D)
Все 2D-виджеты используют виртуальную сетку **`1920 × 1080`**, которая автоматически масштабируется под размер окна и GUI Scale игрока.

### Якоря (`Anchor`)
По умолчанию виджеты привязаны к `TOP_LEFT`. Изменить якорь можно через `setAnchor(id, anchor)`:
* `TOP_LEFT`, `TOP_CENTER`, `TOP_RIGHT`
* `CENTER_LEFT`, `CENTER`, `CENTER_RIGHT`
* `BOTTOM_LEFT`, `BOTTOM_CENTER`, `BOTTOM_RIGHT`

При привязке, например, к `TOP_RIGHT`, координата `x = -200` означает «200 виртуальных пикселей левее правого края экрана».

### Цвета (`ARGB`)
Цвета передаются 32-битным целым числом `0xAARRGGBB`:
* `0xFF00E5FF` — полностью непрозрачный голубой
* `0x88000000` — полупрозрачный чёрный
* Если альфа-канал не задан (`0x00FF00`), клиент автоматически сделает цвет непрозрачным (`0xFF00FF00`).

---

## Методы управления и информации

| Метод | Описание |
| :--- | :--- |
| `getBridgeId()` | Возвращает строковый UUID моста. |
| `getConnectedPlayers()` | Возвращает список таблиц подключённых игроков: `{uuid, name, x, y, z, dimension}`. |
| `getPlayerStats(playerUuid)` | Возвращает телеметрию игрока: `hp`, `maxHp`, `food`, `saturation`, `armor`, `air`, `maxAir`, `xpLevel`, `yaw`, `pitch`, `x`, `y`, `z`, `dimension`, `biome`, `dayTime`, `energy`, `maxEnergy` (или `nil`). |
| `sendMessage(playerUuid, message)` | Отправляет сообщение `[Glasses] ...` в чат конкретному игроку. |
| `broadcastMessage(message)` | Отправляет сообщение в чат всем подключённым к мосту игрокам. |

---

## Методы 2D HUD-виджетов

Для каждого глобального метода существует персональный аналог с суффиксом `Player` (первый аргумент — `playerUuid`), например `addPlayerText(playerUuid, id, ...)`.

| Метод | Параметры | Описание |
| :--- | :--- | :--- |
| `clear()` / `clearPlayer(uuid)` | — | Удаляет все виджеты слоя. |
| `removeWidget(id)` / `removePlayerWidget(uuid, id)` | `id` | Удаляет виджет по ID. |
| `addRect(...)` | `id, x, y, width, height, color` | Рисует прямоугольник. |
| `addText(...)` | `id, x, y, text, color, scale` | Рисует текст с тенью (`scale = 1.0` — базовый размер). |
| `addProgressBar(...)` | `id, x, y, width, height, value, max, fgColor, bgColor` | Рисует прогресс-бар. |
| `addItemIcon(...)` | `id, x, y, itemId, scale` | Рисует иконку предмета (например `"minecraft:diamond"`). |
| `addGraph(...)` | `id, x, y, width, height, values, color, bgColor` | Рисует график по массиву чисел `values` (до 256 точек). |
| `setAnchor(...)` | `id, anchorName` | Устанавливает точку привязки (см. список выше). |
| `setClickable(...)` | `id, clickable` | Делает 2D-виджет кликабельным в режиме курсора (`H`). |

---

## Методы 3D AR-виджетов (World-Space)

| Метод | Параметры | Описание |
| :--- | :--- | :--- |
| `addWorldMarker(...)` | `id, x, y, z, label, color` | 3D-рамка и подпись с дистанцией в точке `(x, y, z)`. |
| `addWorldLine(...)` | `id, x1, y1, z1, x2, y2, z2, color` | 3D-линия между двумя точками в мире. |
| `addEntityMarker(...)` | `id, entityUuid, label, color` | Динамическая рамка и подпись, следующие за сущностью. |

---

## События (`pollEvent` / `getEvents`)

* **`pollEvent()`** — возвращает и удаляет одно событие из очереди (или `nil`).
* **`getEvents()`** — возвращает массив всех накопившихся событий и очищает очередь.

### Типы событий

1. **`glasses_chat_command`** (сообщение в чате с префиксом `$$`)
   * `event`, `playerUuid`, `playerName`, `command`
2. **`glasses_gaze`** (нажатие клавиши `G`, дальность до 64 блоков)
   * Общие поля: `event`, `playerUuid`, `playerName`, `targetType` (`"block"`, `"entity"`, `"miss"`), `hitX`, `hitY`, `hitZ`
   * Для `"block"`: `blockX`, `blockY`, `blockZ`, `face`
   * Для `"entity"`: `entityUuid`, `entityName`
3. **`glasses_hud_click`** (клик по виджету с `clickable = true` в режиме курсора `H`)
   * `event`, `playerUuid`, `playerName`, `widgetId`, `button` (`0` = ЛКМ, `1` = ПКМ)
4. **`glasses_player_connected`** / **`glasses_player_disconnected`**
   * `event`, `playerUuid`, `playerName`
