#!/usr/bin/env python3
"""BT Touchpad — Linux-сторона.
Регистрирует RFCOMM-сервис в BlueZ, принимает команды с телефона
и превращает их в события виртуальной мыши (uinput).
Запуск: sudo python3 server.py
"""
import os
import threading

import dbus
import dbus.mainloop.glib
import dbus.service
from evdev import UInput, ecodes as e
from gi.repository import GLib

UUID = "8f1c2a5e-6b7d-4c1a-9a3e-5d2b7c9e1f40"   # должен совпадать с Android-приложением
PATH = "/org/bttouchpad/profile"
BUTTONS = {"l": e.BTN_LEFT, "r": e.BTN_RIGHT, "m": e.BTN_MIDDLE}
KEYS = {n: c for n, c in e.ecodes.items() if n.startswith("KEY_") and 0 < c < 256}
held = set()

# ---- набор текста: символ -> (клавиша, shift) ----
SYM_US = {}
for _c in "abcdefghijklmnopqrstuvwxyz":
    SYM_US[_c] = ("KEY_" + _c.upper(), False)
    SYM_US[_c.upper()] = ("KEY_" + _c.upper(), True)
for _d in "1234567890":
    SYM_US[_d] = ("KEY_" + _d, False)
for _ch, _k, _s in [
    ("`", "KEY_GRAVE", False), ("~", "KEY_GRAVE", True), ("!", "KEY_1", True), ("@", "KEY_2", True),
    ("#", "KEY_3", True), ("$", "KEY_4", True), ("%", "KEY_5", True), ("^", "KEY_6", True),
    ("&", "KEY_7", True), ("*", "KEY_8", True), ("(", "KEY_9", True), (")", "KEY_0", True),
    ("-", "KEY_MINUS", False), ("_", "KEY_MINUS", True), ("=", "KEY_EQUAL", False), ("+", "KEY_EQUAL", True),
    ("[", "KEY_LEFTBRACE", False), ("{", "KEY_LEFTBRACE", True), ("]", "KEY_RIGHTBRACE", False),
    ("}", "KEY_RIGHTBRACE", True), ("\\", "KEY_BACKSLASH", False), ("|", "KEY_BACKSLASH", True),
    (";", "KEY_SEMICOLON", False), (":", "KEY_SEMICOLON", True), ("'", "KEY_APOSTROPHE", False),
    ('"', "KEY_APOSTROPHE", True), (",", "KEY_COMMA", False), ("<", "KEY_COMMA", True),
    (".", "KEY_DOT", False), (">", "KEY_DOT", True), ("/", "KEY_SLASH", False), ("?", "KEY_SLASH", True),
    (" ", "KEY_SPACE", False), ("\n", "KEY_ENTER", False), ("\t", "KEY_TAB", False),
]:
    SYM_US[_ch] = (_k, _s)

# кириллица = те же физические клавиши, что и в раскладке ЙЦУКЕН
RU_FROM = "йцукенгшщзхъфывапролджэячсмитьбюё"
RU_PHYS = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`"
SYM_RU = {}
for _r, _p in zip(RU_FROM, RU_PHYS):
    SYM_RU[_r] = (SYM_US[_p][0], False)
    SYM_RU[_r.upper()] = (SYM_US[_p][0], True)
SYM_RU.update({
    ".": ("KEY_SLASH", False), ",": ("KEY_SLASH", True), '"': ("KEY_2", True), "№": ("KEY_3", True),
    ";": ("KEY_4", True), ":": ("KEY_6", True), "?": ("KEY_7", True), "/": ("KEY_BACKSLASH", True),
})
for _c in "0123456789 \n\t-_=+\\!%*()":
    SYM_RU[_c] = SYM_US[_c]

lang = "en"   # по последней набранной букве: в ru-режиме знаки препинания берутся из ЙЦУКЕН

ui = UInput(
    {
        e.EV_KEY: [e.BTN_LEFT, e.BTN_RIGHT, e.BTN_MIDDLE],
        e.EV_REL: [e.REL_X, e.REL_Y, e.REL_WHEEL, e.REL_HWHEEL],
    },
    name="BT Touchpad",
)


kbd = UInput({e.EV_KEY: sorted(set(KEYS.values()))}, name="BT Touchpad Keyboard")


def release_all():
    for b in BUTTONS.values():
        ui.write(e.EV_KEY, b, 0)
    ui.syn()
    for c in list(held):
        kbd.write(e.EV_KEY, c, 0)
    held.clear()
    kbd.syn()


def tap_key(name, shift=False):
    code = KEYS[name]
    if shift:
        kbd.write(e.EV_KEY, e.KEY_LEFTSHIFT, 1)
        kbd.syn()
    kbd.write(e.EV_KEY, code, 1)
    kbd.syn()
    kbd.write(e.EV_KEY, code, 0)
    kbd.syn()
    if shift:
        kbd.write(e.EV_KEY, e.KEY_LEFTSHIFT, 0)
        kbd.syn()


def type_char(ch):
    global lang
    if ch in SYM_RU and ch not in SYM_US and ch.lower() in RU_FROM:
        lang = "ru"
    elif ch.isalpha() and ch in SYM_US:
        lang = "en"
    table = SYM_RU if lang == "ru" else SYM_US
    m = table.get(ch)
    if m is None and ch not in SYM_US:
        m = SYM_RU.get(ch)
    if m:
        tap_key(*m)


def handle(line: str):
    p = line.split()
    if not p:
        return
    try:
        cmd = p[0]
        if cmd == "m":                      # m dx dy
            ui.write(e.EV_REL, e.REL_X, int(p[1]))
            ui.write(e.EV_REL, e.REL_Y, int(p[2]))
        elif cmd == "s":                    # s wheel hwheel
            if int(p[1]):
                ui.write(e.EV_REL, e.REL_WHEEL, int(p[1]))
            if int(p[2]):
                ui.write(e.EV_REL, e.REL_HWHEEL, int(p[2]))
        elif cmd == "d":                    # d l|r|m  — нажать
            ui.write(e.EV_KEY, BUTTONS[p[1]], 1)
        elif cmd == "u":                    # u l|r|m  — отпустить
            ui.write(e.EV_KEY, BUTTONS[p[1]], 0)
        elif cmd == "c":                    # c l|r|m  — клик
            ui.write(e.EV_KEY, BUTTONS[p[1]], 1)
            ui.syn()
            ui.write(e.EV_KEY, BUTTONS[p[1]], 0)
        elif cmd == "t":                    # t <hex> <hex> … — набрать текст
            for h in p[1:]:
                type_char(chr(int(h, 16)))
            return
        elif cmd == "p":                    # p KEY_NAME — нажать и отпустить
            tap_key(p[1])
            return
        elif cmd == "k":                    # k KEY_NAME 1|0  — клавиша вниз/вверх
            code = KEYS[p[1]]
            down = int(p[2])
            kbd.write(e.EV_KEY, code, 1 if down else 0)
            kbd.syn()
            if down:
                held.add(code)
            else:
                held.discard(code)
            return
        else:
            return
        ui.syn()
    except (IndexError, ValueError, KeyError, OverflowError):
        pass


def reader(fd: int):
    buf = b""
    try:
        # BlueZ отдаёт сокет в неблокирующем режиме — без этого os.read()
        # сразу падает с BlockingIOError и соединение закрывается.
        os.set_blocking(fd, True)
        while True:
            data = os.read(fd, 1024)
            if not data:
                break
            buf += data
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                handle(line.decode(errors="ignore"))
    except OSError as err:
        print("Ошибка чтения:", err)
    finally:
        release_all()
        try:
            os.close(fd)
        except OSError:
            pass
        print("Телефон отключился")


class Profile(dbus.service.Object):
    @dbus.service.method("org.bluez.Profile1", in_signature="", out_signature="")
    def Release(self):
        pass

    @dbus.service.method("org.bluez.Profile1", in_signature="oha{sv}", out_signature="")
    def NewConnection(self, device, fd, props):
        print("Подключено:", device)
        threading.Thread(target=reader, args=(fd.take(),), daemon=True).start()

    @dbus.service.method("org.bluez.Profile1", in_signature="o", out_signature="")
    def RequestDisconnection(self, device):
        pass


def main():
    dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
    bus = dbus.SystemBus()
    Profile(bus, PATH)
    mgr = dbus.Interface(bus.get_object("org.bluez", "/org/bluez"), "org.bluez.ProfileManager1")
    mgr.RegisterProfile(PATH, UUID, {
        "Name": "BT Touchpad",
        "Role": "server",
        "Channel": dbus.UInt16(22),
        "RequireAuthentication": False,
        "RequireAuthorization": False,
    })
    print("BT Touchpad ждёт подключения телефона… (Ctrl+C — выход)")
    try:
        GLib.MainLoop().run()
    except KeyboardInterrupt:
        pass
    finally:
        release_all()
        ui.close()
        kbd.close()


if __name__ == "__main__":
    main()
