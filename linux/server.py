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

ui = UInput(
    {
        e.EV_KEY: [e.BTN_LEFT, e.BTN_RIGHT, e.BTN_MIDDLE],
        e.EV_REL: [e.REL_X, e.REL_Y, e.REL_WHEEL, e.REL_HWHEEL],
    },
    name="BT Touchpad",
)


def release_all():
    for b in BUTTONS.values():
        ui.write(e.EV_KEY, b, 0)
    ui.syn()


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
        else:
            return
        ui.syn()
    except (IndexError, ValueError, KeyError):
        pass


def reader(fd: int):
    buf = b""
    try:
        while True:
            data = os.read(fd, 1024)
            if not data:
                break
            buf += data
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                handle(line.decode(errors="ignore"))
    except OSError:
        pass
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


if __name__ == "__main__":
    main()
