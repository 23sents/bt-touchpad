# BT Touchpad — телефон (Android) как тачпад для Linux по Bluetooth

## Linux
    sudo apt install bluez python3-dbus python3-gi python3-evdev
    sudo python3 linux/server.py
Нужен root (доступ к /dev/uinput и регистрация сервиса в BlueZ).
Сначала спарьте телефон с ПК (bluetoothctl или настройки Bluetooth).

## Android
Откройте папку `android/` в Android Studio, дождитесь синхронизации Gradle,
подключите телефон (отладка по USB) и нажмите Run. Затем выберите ПК в списке.

## Управление
- 1 палец — курсор; тап — ЛКМ
- 2 пальца — прокрутка; тап двумя пальцами — ПКМ
- Свайп влево от правого края экрана (или кнопка ⌨) — экранная клавиатура; ▼ — скрыть
- Клавиатура шлёт физические клавиши: язык ввода переключается на ПК. Shift/Ctrl/Alt/Win залипают на одно нажатие. EN/RU меняет только подписи
- Кнопки ЛКМ/ПКМ внизу (можно удерживать — перетаскивание)
- Чувствительность: `gain` в TouchpadView.kt

## Протокол (текст, по строке)
m dx dy | s wheel hwheel | d/u/c l|r|m
