# Архитектура кода мода

Проект собран под **Minecraft 1.21.1 / NeoForge 21.1** с использованием Java 21 и локального JAR-артефакта OC2R (`libs/oc2r-1.21.1-neoforge-0.1.1-beta.4.jar`).

## Структура пакетов (`src/main/java/org/example/smartglasses/`)

```text
org.example.smartglasses
├── SmartGlasses.java                   # Точка входа, регистрация блоков/предметов/пакетов/событий
├── Config.java                         # Конфиг мода (дальность, энергия, междименсионность)
├── block/
│   └── GlassesBridgeBlock.java         # Блок моста и серверный тикер
├── blockentity/
│   ├── BridgeRegistry.java             # Потокобезопасный реестр активных мостов (UUID -> BE)
│   └── GlassesBridgeBlockEntity.java   # Логика HUD/AR, diff-синхронизация, RPC-методы и очередь событий
├── item/
│   └── SmartGlassesItem.java           # Предмет очков, привязка к мосту, FE-батарея, Curios
├── hud/
│   ├── Anchor.java                     # 9 якорей привязки (TOP_LEFT .. BOTTOM_RIGHT)
│   └── HudWidget.java                  # Иммутабельная модель виджета и StreamCodec
├── network/
│   ├── SyncHudPayload.java             # Server -> Client: diff или полный снимок виджетов
│   ├── GlassesGazePayload.java         # Client -> Server: цель взгляда (клавиша G)
│   └── GlassesHudClickPayload.java     # Client -> Server: клик по виджету (клавиша H)
├── client/
│   ├── ClientHudState.java             # Клиентский кэш активных виджетов
│   ├── SmartGlassesHudRenderer.java    # 2D-рендер HUD (виртуальная сетка 1920x1080) + hitTest
│   ├── SmartGlassesWorldRenderer.java  # 3D AR-рендер (маркеры, линии, рамки сущностей)
│   ├── SmartGlassesCursorScreen.java   # Прозрачный экран для кликов по HUD
│   └── SmartGlassesClientEvents.java   # Клавиши G/H, raycast взгляда, очистка при выходе
└── mixin/
    └── CustomHeadLayerMixin.java       # Скрывает предмет очков над головой игрока
```

---

## Как устроена интеграция с OC2R

В `OC2R 0.1.1-beta.4` (1.21.1) capability устройства шины обёрнута платформенным классом `li.cil.oc2.platform.BlockCapability`. В `SmartGlasses.registerCapabilities(...)` создаётся NeoForge `BlockCapability` с тем же ID (`Capabilities.Device.BLOCK.getId()`, т.е. `oc2r:device`) и регистрируется `ObjectDevice`:

```java
BlockCapability<Device, Direction> oc2rDeviceCap = BlockCapability.createSided(
        Capabilities.Device.BLOCK.getId(),
        Device.class
);
event.registerBlockEntity(
        oc2rDeviceCap,
        GLASSES_BRIDGE_BE.get(),
        (be, side) -> new ObjectDevice(be, List.of("glasses_bridge"))
);
```

`ObjectDevice` автоматически сканирует методы с аннотацией `@Callback` в `GlassesBridgeBlockEntity` и делает их доступными из Linux внутри виртуальной машины RISC-V.

### Двусторонняя связь (события)
`GlassesBridgeBlockEntity` реализует `RPCEventSource` (для нативной подписки OC2R через `IEventSink`) **и дополнительно** складывает все события в потокобезопасную очередь `ConcurrentLinkedQueue` (лимит `256` событий), доступную через `@Callback` `pollEvent()` / `getEvents()`. Это позволяет писать простые циклы обработки событий в Lua и Python без работы с низкоуровневым `/dev/hvc0`.

---

## Синхронизация HUD (Diff-протокол)

Чтобы скрипты могли часто обновлять виджеты без лишней нагрузки на сеть:
1. Вызовы `addText`, `addRect`, `removeWidget`, `clear` и т.д. не отправляют пакеты мгновенно, а помечают `id` в `globalDirtyUpserts` / `globalDirtyRemoves` (или в `playerDiffs` для персонального слоя).
2. Раз в тик в `GlassesBridgeBlockEntity.serverTick()`:
   * Проверяется список игроков с надетыми и привязанными очками в пределах `Config.maxRange`.
   * Новым игрокам отправляется полный снимок (`SyncHudPayload` с `clearAll = true`).
   * Отключившимся игрокам отправляется очистка HUD.
   * Активным игрокам отправляется только дельта изменений (`removedIds` + `upserts`).
3. Виджет в персональном слое игрока с тем же `id` перекрывает глобальный виджет.

---

## Клиентский рендер и скрытие модели на голове

* **2D HUD (`SmartGlassesHudRenderer`):** регистрируется через `RegisterGuiLayersEvent` поверх `VanillaGuiLayers.CROSSHAIR`. Координаты масштабируются из виртуального пространства `1920×1080` коэффициентом `min(screenW / 1920, screenH / 1080)` относительно выбранного `Anchor`.
* **3D AR (`SmartGlassesWorldRenderer`):** работает на `RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS`, рисует линии, 3D-рамки (`LevelRenderer.renderLineBox`) и текст с `Font.DisplayMode.SEE_THROUGH` (виден сквозь блоки).
* **Скрытие на голове (`CustomHeadLayerMixin`):** перехватывает `CustomHeadLayer.render` и отменяет отрисовку, если в слоте `EquipmentSlot.HEAD` надет `SmartGlassesItem`.

---

## Энергия, Curios и Конфиг

* **Энергия (FE):** хранится в `DataComponentType<Integer>` (`SmartGlasses.ENERGY`), обёрнута в `IEnergyStorage` (`SmartGlassesItem.EnergyStorage`). По умолчанию `requireEnergy = false` в `Config.java`.
* **Curios API:** поддерживается мягко через `ModList.get().isLoaded("curios")` + рефлексию в `SmartGlassesItem.getEquippedGlasses(Player)` и тег `data/curios/tags/item/head.json`. Жёсткой зависимости от Curios при компиляции нет.
