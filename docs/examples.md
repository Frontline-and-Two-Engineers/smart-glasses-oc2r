# Примеры программ для OC2R

Перед запуском Lua-скриптов в Linux внутри OC2R убедитесь, что `LUA_PATH` видит системные библиотеки:
```sh
export LUA_PATH="/lib/lua/?.lua;/usr/lib/lua/?.lua;./?.lua;;"
```

---

## 1. Интерактивная панель управления (Кликабельный HUD)

Нажмите **`H`** в игре, чтобы появился курсор, и кликните по кнопкам на экране.

```lua
#!/usr/bin/lua
local devices = require("devices")
local g = assert(devices:find("glasses_bridge"), "Bridge not found")

local lightsOn = false

local function drawMenu()
    g:clear()
    g:addRect("panel", 20, 20, 260, 130, 0xAA101820)
    g:addText("hdr", 35, 32, "BASE CONTROL", 0xFF00E5FF, 1.0)

    local btnColor = lightsOn and 0xCC1E6B38 or 0xCC6B1E1E
    local btnLabel = lightsOn and "Lights: ON" or "Lights: OFF"

    g:addRect("btn_lights", 35, 65, 230, 34, btnColor)
    g:addItemIcon("ico_lights", 40, 70, "minecraft:redstone_lamp", 0.75)
    g:addText("txt_lights", 78, 74, btnLabel, 0xFFFFFFFF, 0.9)
    g:setClickable("btn_lights", true)

    g:addRect("btn_clear", 35, 108, 230, 28, 0xCC2A3440)
    g:addText("txt_clear", 85, 114, "Hide HUD", 0xFFCCCCCC, 0.85)
    g:setClickable("btn_clear", true)
end

drawMenu()

while true do
    local ev = g:pollEvent()
    if ev then
        if ev.event == "glasses_hud_click" then
            if ev.widgetId == "btn_lights" then
                lightsOn = not lightsOn
                drawMenu()
                g:sendMessage(ev.playerUuid, "Свет: " .. (lightsOn and "ВКЛ" or "ВЫКЛ"))
            elseif ev.widgetId == "btn_clear" then
                g:clear()
                g:sendMessage(ev.playerUuid, "HUD скрыт")
                break
            end
        end
    else
        os.execute("sleep 0.1")
    end
end
```

---

## 2. Телеметрия игрока («Костюм Джарвиса») + График

Показывает HP, броню, координаты и живой график заряда/высоты в правом верхнем углу.

```lua
#!/usr/bin/lua
local devices = require("devices")
local g = assert(devices:find("glasses_bridge"), "Bridge not found")

g:clear()
local history = {}

while true do
    local players = g:getConnectedPlayers()
    for _, p in ipairs(players) do
        local st = g:getPlayerStats(p.uuid)
        if st then
            g:addPlayerRect(p.uuid, "bg", -320, 20, 300, 170, 0x990D1520)
            g:setPlayerAnchor(p.uuid, "bg", "TOP_RIGHT")

            g:addPlayerText(p.uuid, "name", -305, 32, st.name .. " | " .. st.biome, 0xFF00E5FF, 0.8)
            g:setPlayerAnchor(p.uuid, "name", "TOP_RIGHT")

            g:addPlayerProgressBar(p.uuid, "hp", -305, 58, 270, 18, st.hp, st.maxHp, 0xFFFF4444, 0xFF331111)
            g:setPlayerAnchor(p.uuid, "hp", "TOP_RIGHT")

            local coords = string.format("XYZ: %.0f / %.0f / %.0f", st.x, st.y, st.z)
            g:addPlayerText(p.uuid, "pos", -305, 86, coords, 0xFFEEEEEE, 0.85)
            g:setPlayerAnchor(p.uuid, "pos", "TOP_RIGHT")

            table.insert(history, st.y)
            if #history > 30 then
                table.remove(history, 1)
            end
            g:addPlayerGraph(p.uuid, "y_graph", -305, 115, 270, 60, history, 0xFF55FF55, 0x66000000)
            g:setPlayerAnchor(p.uuid, "y_graph", "TOP_RIGHT")
        end
    end
    os.execute("sleep 0.5")
end
```

---

## 3. Чат-команды (`$$`) и 3D AR-сканер (`G`)

* Напишите в чат `$$wp Дом` — поставит 3D-метку на ваши текущие координаты.
* Нажмите **`G`**, глядя на блок или моба — поставит на них 3D AR-подсветку.

```lua
#!/usr/bin/lua
local devices = require("devices")
local g = assert(devices:find("glasses_bridge"), "Bridge not found")

g:clear()
local wpCount = 0

while true do
    local ev = g:pollEvent()
    if ev then
        if ev.event == "glasses_chat_command" then
            if ev.command:sub(1, 3) == "wp " then
                local st = g:getPlayerStats(ev.playerUuid)
                if st then
                    wpCount = wpCount + 1
                    local name = ev.command:sub(4)
                    g:addWorldMarker("wp_" .. wpCount, st.x, st.y + 1, st.z, name, 0xFF00FFFF)
                    g:sendMessage(ev.playerUuid, "Метка поставлена: " .. name)
                end
            elseif ev.command == "clear" then
                g:clear()
                g:sendMessage(ev.playerUuid, "Все метки очищены")
            end

        elseif ev.event == "glasses_gaze" then
            if ev.targetType == "block" then
                g:addWorldMarker("gaze_blk", ev.blockX + 0.5, ev.blockY + 0.5, ev.blockZ + 0.5, "Target", 0xFFFFAA00)
            elseif ev.targetType == "entity" and ev.entityUuid then
                g:addEntityMarker("gaze_ent", ev.entityUuid, ev.entityName or "Entity", 0xFFFF3355)
            end
        end
    else
        os.execute("sleep 0.1")
    end
end
```

---

## 4. Пример на MicroPython (встроенном в OC2R)

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
