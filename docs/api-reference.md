# Справочник API

Все устройства аддона доступны через стандартный модуль OC2R `devices`:

```lua
local devices = require("devices")
local g = assert(devices:find("glasses_bridge"))
```

---

## 1. Мост умных очков (`glasses_bridge`)

Подключается к компьютеру по **Bus Cable**. Уровень возможностей зависит от надетых игроком очков:
- **Уровень 1 (`smart_glasses`)**: 2D HUD.
- **Уровень 2 (`smart_glasses_tier_2`)**: 2D HUD + курсор (`H`) + чат-команды (`$$`).
- **Уровень 3 (`smart_glasses_tier_3`)**: 2D HUD + курсор/чат + 3D AR в мире + сканер взгляда (`G`).

### Базовые понятия

#### Виртуальные координаты (2D)
Все 2D-виджеты используют виртуальную сетку **`1920 × 1080`**, которая автоматически масштабируется под размер окна и GUI Scale игрока.

#### Относительные 3D-координаты
По умолчанию в конфиге включено **`relativeCoordinates = true`**:
* `getConnectedPlayers()`, `getPlayerStats()` и событие `glasses_gaze` возвращают координаты **относительно блока `glasses_bridge`** (`pos - bridgePos`).
* `addWorldMarker()` и `addWorldLine()` принимают координаты **относительно блока `glasses_bridge`** (сервер сам переводит их в мировые для рендера на клиенте).
* Если в конфиге задать `relativeCoordinates = false`, все координаты становятся абсолютными мировыми.

#### Якоря (`Anchor`)
По умолчанию виджеты привязаны к `TOP_LEFT`. Изменить якорь можно через `setAnchor(id, anchor)`:
* `TOP_LEFT`, `TOP_CENTER`, `TOP_RIGHT`
* `CENTER_LEFT`, `CENTER`, `CENTER_RIGHT`
* `BOTTOM_LEFT`, `BOTTOM_CENTER`, `BOTTOM_RIGHT`

#### Цвета (`ARGB`)
Цвета передаются 32-битным целым числом `0xAARRGGBB` (`0xFF00E5FF` — непрозрачный голубой, `0x88000000` — полупрозрачный чёрный). Если альфа-канал равен `00` (например `0x00FF00`), клиент автоматически сделает цвет непрозрачным.

### Методы управления и информации

| Метод | Описание |
| :--- | :--- |
| `getBridgeId()` | Возвращает строковый UUID моста. |
| `isRelativeCoordinates()` | Возвращает `true`, если координаты относительны блока моста. |
| `getConnectedPlayers()` | Возвращает список таблиц: `{uuid, name, tier, x, y, z, dimension}`. |
| `getPlayerStats(playerUuid)` | Телеметрия игрока: `uuid`, `name`, `hp`, `maxHp`, `food`, `saturation`, `armor`, `air`, `maxAir`, `xpLevel`, `yaw`, `pitch`, `x`, `y`, `z`, `dimension`, `biome`, `dayTime`, `energy`, `maxEnergy` (или `nil`). |
| `sendMessage(playerUuid, message)` | Отправляет сообщение `[Glasses] ...` в чат конкретному игроку. |
| `broadcastMessage(message)` | Отправляет сообщение в чат всем подключённым к мосту игрокам. |

### Методы 2D HUD-виджетов

Для каждого глобального метода существует персональный аналог с суффиксом `Player` (первый аргумент — `playerUuid`), например `addPlayerText(playerUuid, id, ...)`.

| Метод | Параметры | Описание |
| :--- | :--- | :--- |
| `clear()` / `clearPlayer(uuid)` | — | Удаляет все виджеты слоя. |
| `removeWidget(id)` / `removePlayerWidget(uuid, id)` | `id` | Удаляет виджет по ID. |
| `addRect(...)` | `id, x, y, width, height, color` | Рисует прямоугольник. |
| `addText(...)` | `id, x, y, text, color, scale` | Рисует текст с тенью (`scale = 1.0` — базовый размер). |
| `addProgressBar(...)` | `id, x, y, width, height, value, max, fgColor, bgColor` | Рисует прогресс-бар. |
| `addItemIcon(...)` | `id, x, y, itemId, scale` | Рисует иконку предмета (`"minecraft:diamond"`). |
| `addGraph(...)` | `id, x, y, width, height, values, color, bgColor` | Рисует график по массиву чисел `values` (до 256 точек). |
| `setAnchor(...)` | `id, anchorName` | Устанавливает точку привязки. |
| `setClickable(...)` | `id, clickable` | Делает 2D-виджет кликабельным в режиме курсора (`H`, Tier 2+). |

### Методы 3D AR-виджетов (Tier 3)

| Метод | Параметры | Описание |
| :--- | :--- | :--- |
| `addWorldMarker(...)` | `id, x, y, z, label, color` | 3D-рамка и подпись с дистанцией в точке `(x, y, z)`. |
| `addWorldLine(...)` | `id, x1, y1, z1, x2, y2, z2, color` | 3D-линия между двумя точками. |
| `addEntityMarker(...)` | `id, entityUuid, label, color` | Динамическая рамка и подпись, следующие за сущностью. |

### События (`pollEvent` / `getEvents`)

* **`pollEvent()`** — возвращает и удаляет одно событие из очереди (или `nil`).
* **`getEvents()`** — возвращает массив всех накопившихся событий и очищает очередь.

1. **`glasses_chat_command`** (Tier 2+, сообщение в чате с префиксом `$$`)
   * `event`, `playerUuid`, `playerName`, `command`
2. **`glasses_gaze`** (Tier 3, клавиша `G`, дальность до 64 блоков)
   * Общие поля: `event`, `playerUuid`, `playerName`, `targetType` (`"block"`, `"entity"`, `"miss"`), `hitX`, `hitY`, `hitZ`
   * Для `"block"`: `blockX`, `blockY`, `blockZ`, `face`
   * Для `"entity"`: `entityUuid`, `entityName`
3. **`glasses_hud_click`** (Tier 2+, клик по виджету с `clickable = true` в режиме курсора `H`)
   * `event`, `playerUuid`, `playerName`, `widgetId`, `button` (`0` = ЛКМ, `1` = ПКМ)
4. **`glasses_player_connected`** / **`glasses_player_disconnected`**
   * `event`, `playerUuid`, `playerName`

---

## 2. Радар сущностей (`radar`)

Блоки `radar_tier_1`, `radar_tier_2`, `radar_tier_3` подключаются по **Bus Cable**.

| Уровень | Макс. радиус | Поля каждой записи в `scan(radius)` |
| :--- | :--- | :--- |
| **Tier 1** | `64` блоков | `uuid`, `type`, `distance` |
| **Tier 2** | `128` блоков | + `hp`, `maxHp` (для живых существ) |
| **Tier 3** | `256` блоков | + `name`, `isPlayer`, `x`, `y`, `z` (относительно радара, если `relativeCoordinates = true`) |

| Метод | Описание |
| :--- | :--- |
| `getTier()` | Возвращает уровень радара (`1`, `2` или `3`). |
| `getMaxRange()` | Возвращает максимальный радиус (`64.0`, `128.0` или `256.0`). |
| `isRelativeCoordinates()` | Возвращает `true`, если координаты сущностей относительны блока радара. |
| `scan(radius)` | Сканирует сущности в радиусе (ограничивается максимумом уровня; до 64 ближайших). |

---

## 3. Беспроводная сетевая карта (`wireless_card`) и Ретранслятор

Карта вставляется в слот карт расширения компьютера. Позволяет обмениваться строковыми пакетами по портам (`1..65535`).

| Метод | Описание |
| :--- | :--- |
| `getAddress()` | Возвращает UUID карты (строка). |
| `open(port)` / `close(port)` / `closeAll()` | Открывает/закрывает порт (`1..65535`). |
| `isOpen(port)` / `getOpenPorts()` | Проверяет статус порта или возвращает список открытых портов. |
| `setStrength(range)` / `getStrength()` | Задаёт/читает дальность передачи (ограничивается `wirelessMaxRange`). |
| `broadcast(port, payload)` | Широковещательная отправка всем картам с открытым портом в радиусе; возвращает число доставленных карт. |
| `send(addressOrPrefix, port, payload)` | Адресная отправка по полному UUID **или короткому префиксу** (например `"7c571a5f"`); возвращает `true`/`false`. |
| `pollMessage()` | Возвращает и удаляет следующее сообщение: `{from, port, distance, payload}` (или `nil`). |
| `getMessages()` | Возвращает и очищает всю очередь сообщений. |

### Беспроводной ретранслятор (`wireless_repeater`)
Блок-ретранслятор питается от **FE-энергии** и автоматически пересылает пакеты дальше по цепочке (до 4 хопов, без зацикливания). Клик **ПКМ** пустой рукой показывает текущий заряд FE.

---

## 4. Безопасная карта Pastebin и GitHub (`pastebin`)

Карта вставляется в слот карт расширения компьютера. Делает HTTPS-запросы только к разрешённым хостам (`pastebin.com`, `raw.githubusercontent.com`, `gist.githubusercontent.com`) с блокировкой локальных/приватных IP, редиректов, ограничением размера и кулдауном.

| Метод | Описание |
| :--- | :--- |
| `fetchPastebin(idOrUrl)` | Скачивает raw-текст с Pastebin по ID (`"AbCdEf12"`) или URL (`"https://pastebin.com/AbCdEf12"` / `/raw/...`). |
| `fetchGitHub(owner, repo, branch, path)` | Скачивает файл с `raw.githubusercontent.com`. |
| `fetchUrl(url)` | Скачивает raw-текст по разрешённому HTTPS URL (`pastebin.com`, `raw.githubusercontent.com`, `gist.githubusercontent.com`). |

---

## 5. Конфигурация (`config/smartglasses-common.toml`)

| Параметр | По умолчанию | Описание |
| :--- | :--- | :--- |
| `relativeCoordinates` | `true` | Относительные координаты (от блока моста/радара) вместо абсолютных мировых. |
| `maxRange` | `256` | Максимальная дистанция работы очков от моста (`0` = без ограничений). |
| `allowCrossDimension` | `false` | Работают ли очки при переходе в другое измерение. |
| `requireEnergy` | `false` | Требуется ли FE-энергия на очках для отображения HUD/AR. |
| `energyCapacity` | `100000` | Ёмкость FE-энергии умных очков. |
| `energyPerTick` | `5` | Расход FE/тик при `requireEnergy = true`. |
| `wirelessMaxRange` | `512` | Максимальный радиус передачи беспроводной карты. |
| `repeaterRange` | `512` | Радиус ретрансляции беспроводного ретранслятора. |
| `repeaterEnergyCapacity` | `50000` | Ёмкость FE-энергии ретранслятора. |
| `repeaterEnergyPerPacket` | `25` | Расход FE на один ретранслированный пакет. |
| `enablePastebinCard` | `true` | Разрешить исходящие HTTPS-запросы для карты Pastebin/GitHub. |
| `pastebinMaxBytes` | `262144` | Макс. размер ответа в байтах (256 KiB). |
| `pastebinCooldownMs` | `2000` | Минимальная задержка между запросами в мс. |
