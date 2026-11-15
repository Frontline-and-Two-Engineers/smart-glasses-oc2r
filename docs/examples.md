# Примеры программ для OC2R

Все примеры рассчитаны на запуск в Linux внутри OC2R (`lua script.lua` или `python3 script.py`). Предварительно убедитесь, что `LUA_PATH="/mnt/builtin/lib/lua/?.lua"` (встроенный образ OC2R уже настраивает это по умолчанию).

---

## 1. Интерактивная панель с кнопкой (`H`) и чат-командами (`$$`)

> Требуются очки **Ур. 2** или **Ур. 3**.

```lua
local devices = require("devices")
local g = assert(devices:find("glasses_bridge"))

g:clear()
g:addRect("panel", 20, 20, 320, 120, 0xAA101820)
g:addText("title", 35, 32, "База: статус", 0xFF00E5FF, 1.1)

g:addRect("btn_ping", 35, 70, 130, 34, 0xFF1F6FEB)
g:addText("btn_ping_txt", 52, 80, "Пинг", 0xFFFFFFFF, 1.0)
g:setClickable("btn_ping", true)

g:addRect("btn_clear", 180, 70, 140, 34, 0xFFDA3633)
g:addText("btn_clear_txt", 195, 80, "Очистить", 0xFFFFFFFF, 1.0)
g:setClickable("btn_clear", true)

g:broadcastMessage("Панель загружена! Нажмите H для клика или введите $$ping в чат.")

while true do
    local ev = g:pollEvent()
    if ev then
        if ev.event == "glasses_hud_click" then
            if ev.widgetId == "btn_ping" then
                g:sendMessage(ev.playerUuid, "Кнопка нажата!")
            elseif ev.widgetId == "btn_clear" then
                g:clear()
                break
            end
        elseif ev.event == "glasses_chat_command" then
            if ev.command == "ping" then
                g:sendMessage(ev.playerUuid, "Pong!")
            end
        end
    else
        os.execute("sleep 0.1")
    end
end
```

---

## 2. Живой HUD телеметрии и график HP

```lua
local devices = require("devices")
local g = assert(devices:find("glasses_bridge"))

local history = {}

while true do
    local players = g:getConnectedPlayers()
    for _, p in ipairs(players) do
        local s = g:getPlayerStats(p.uuid)
        if s then
            table.insert(history, s.hp)
            if #history > 40 then table.remove(history, 1) end

            g:addPlayerRect(p.uuid, "bg", -260, 20, 240, 115, 0x9912161E)
            g:setPlayerAnchor(p.uuid, "bg", "TOP_RIGHT")

            g:addPlayerText(p.uuid, "hp_lbl", -245, 30,
                string.format("HP: %.1f / %.0f | XP: %d", s.hp, s.maxHp, s.xpLevel),
                0xFFFF6B6B, 0.9)
            g:setPlayerAnchor(p.uuid, "hp_lbl", "TOP_RIGHT")

            g:addPlayerProgressBar(p.uuid, "hp_bar", -245, 48, 210, 12,
                s.hp, s.maxHp, 0xFFFF5555, 0xFF2A2F3A)
            g:setPlayerAnchor(p.uuid, "hp_bar", "TOP_RIGHT")

            g:addPlayerGraph(p.uuid, "hp_graph", -245, 68, 210, 52,
                history, 0xFF55FF55, 0x55000000)
            g:setPlayerAnchor(p.uuid, "hp_graph", "TOP_RIGHT")
        end
    end
    os.execute("sleep 0.5")
end
```

---

## 3. 3D AR-сканер взгляда (`G`)

> Требуются очки **Ур. 3**. Работает одинаково с `relativeCoordinates = true` и `false`.

```lua
local devices = require("devices")
local g = assert(devices:find("glasses_bridge"))

g:broadcastMessage("Нажмите G, смотря на блок или моба, чтобы поставить AR-метку.")

local counter = 0
while true do
    local ev = g:pollEvent()
    if ev then
        if ev.event == "glasses_gaze" then
            counter = counter + 1
            if ev.targetType == "block" then
                g:addWorldMarker("wp_" .. counter,
                    ev.blockX + 0.5, ev.blockY + 1.2, ev.blockZ + 0.5,
                    "Цель #" .. counter, 0xFF00E5FF)
                g:sendMessage(ev.playerUuid, string.format(
                    "Метка #%d поставлена (отн. моста: %d, %d, %d)",
                    counter, ev.blockX, ev.blockY, ev.blockZ))
            elseif ev.targetType == "entity" and ev.entityUuid then
                g:addEntityMarker("ent_" .. counter,
                    ev.entityUuid, ev.entityName or "Цель", 0xFFFF5555)
                g:sendMessage(ev.playerUuid, "Отслеживается: " .. (ev.entityName or "?"))
            end
        end
    else
        os.execute("sleep 0.1")
    end
end
```

---

## 4. Радар сущностей + вывод в AR-очки

> Для координат и имён нужен **Радар Ур. 3** и **Очки Ур. 3**.  
> Если `radar` и `glasses_bridge` стоят в соседних блоках (или известна разница позиций), относительные координаты переводятся напрямую.

```lua
local devices = require("devices")
local radar = assert(devices:find("radar"))
local g = devices:find("glasses_bridge")

local tier = radar:getTier()
local range = radar:getMaxRange()
print(string.format("Radar Tier %d (range %.0f, relative=%s)",
    tier, range, tostring(radar:isRelativeCoordinates())))

local hits = radar:scan(range)
for i, e in ipairs(hits) do
    local line = string.format("%2d. %-22s dist=%5.1f", i, e.name or e.type, e.distance)
    if e.hp then
        line = line .. string.format(" HP=%.1f/%.1f", e.hp, e.maxHp)
    end
    if e.x then
        line = line .. string.format(" (%.1f, %.1f, %.1f)", e.x, e.y, e.z)
    end
    if e.isPlayer then line = line .. " [PLAYER]" end
    print(line)

    -- Подсветка найденных игроков в AR через UUID сущности (работает при любых координатах!)
    if g and e.isPlayer then
        g:addEntityMarker("radar_" .. e.uuid, e.uuid, e.name, 0xFFFACC15)
    end
end
```

---

## 5. Беспроводная сеть: приёмник и отправитель (с коротким префиксом)

### Приёмник (`rx.lua`)
```lua
local devices = require("devices")
local card = assert(devices:find("wireless_card"))

card:open(42)
print("Full address : " .. card:getAddress())
print("Short prefix : " .. card:getAddress():sub(1, 8))

while true do
    local msg = card:pollMessage()
    if msg then
        print(string.format("[%s | port %d | dist %.1f] %s",
            tostring(msg.from):sub(1, 8), msg.port, msg.distance or 0, tostring(msg.payload)))
    end
    os.execute("sleep 0.1")
end
```

### Отправитель (`tx.lua`)
```lua
local devices = require("devices")
local card = assert(devices:find("wireless_card"))

card:open(42)
card:setStrength(256)

-- Широковещательная рассылка (подхватывается ретрансляторами Wireless Repeater)
local delivered = card:broadcast(42, "Всем привет!")
print("Доставлено карт: " .. delivered)

-- Адресная отправка по короткому префиксу (первые 8 символов адреса получателя)
local ok = card:send("7c571a5f", 42, "Личное сообщение!")
print("Отправлено по префиксу: " .. tostring(ok))
```

---

## 6. Загрузка скрипта с GitHub / Pastebin и сохранение на диск

```lua
local devices = require("devices")
local card = assert(devices:find("pastebin"))

local code = card:fetchGitHub("Frontline-and-Two-Engineers", "smart-glasses-oc2r", "main", "README.md")
print("Загружено байт: " .. #code)
print(code:sub(1, 300))

-- Пример сохранения Lua-скрипта с Pastebin в файл:
-- local luaCode = card:fetchPastebin("AbCdEf12")
-- local f = assert(io.open("/root/downloaded.lua", "w"))
-- f:write(luaCode)
-- f:close()
```

---

## 7. Пример на MicroPython (встроенном в OC2R)

```python
import time
from devices import find_device

g = find_device("glasses_bridge")
g.clear()
g.addText("py_hello", 20, 20, "Hello from Python on RISC-V!", 0xFF00FFAA, 1.1)

while True:
    ev = g.pollEvent()
    if ev and ev.get("event") == "glasses_chat_command":
        g.sendMessage(ev["playerUuid"], "Python получил: " + ev["command"])
    time.sleep(0.1)
```
