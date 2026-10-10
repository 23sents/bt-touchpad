package com.example.bttouchpad

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class MainActivity : Activity() {
    // Тот же UUID, что и в linux/server.py
    private val uuid: UUID = UUID.fromString("8f1c2a5e-6b7d-4c1a-9a3e-5d2b7c9e1f40")
    private val io = Executors.newSingleThreadExecutor()
    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null
    private var adapter: BluetoothAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
        } else {
            showDevices()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) showDevices()
        else toast("Нужно разрешение на Bluetooth")
    }

    private fun showDevices() {
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            toast("Включите Bluetooth и перезапустите приложение")
            return
        }
        val devices: List<BluetoothDevice> = bt.bondedDevices.toList()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 64, 32, 32) }
        root.addView(TextView(this).apply {
            text = "Выберите компьютер\n(сначала спарьте его в настройках Bluetooth и запустите server.py)"
            textSize = 16f
        })
        val list = ListView(this)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            devices.map { "${it.name ?: "?"}\n${it.address}" })
        list.setOnItemClickListener { _, _, pos, _ -> connect(devices[pos]) }
        root.addView(list)
        setContentView(root)
    }

    private fun connect(dev: BluetoothDevice) {
        toast("Подключение…")
        io.execute {
            try {
                adapter?.cancelDiscovery()
                val s = dev.createRfcommSocketToServiceRecord(uuid)
                s.connect()
                socket = s; out = s.outputStream
                runOnUiThread { showPad() }
            } catch (e: IOException) {
                runOnUiThread { toast("Не удалось подключиться: сервер запущен на ПК?") }
            }
        }
    }

    private fun send(line: String) {
        io.execute {
            try {
                out?.apply { write((line + "\n").toByteArray()); flush() }
            } catch (e: IOException) {
                runOnUiThread { disconnect(); showDevices(); toast("Соединение потеряно") }
            }
        }
    }

    private fun mouseButton(label: String, code: String): Button = Button(this).apply {
        text = label
        setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { send("d $code"); v.isPressed = true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { send("u $code"); v.isPressed = false }
            }
            true
        }
    }

    private var kb: View? = null

    private fun toggleKeyboard(show: Boolean? = null) {
        val v = kb ?: return
        val want = show ?: (v.visibility != View.VISIBLE)
        v.visibility = if (want) View.VISIBLE else View.GONE
    }

    private fun showPad() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(TouchpadView(this, { send(it) }, { toggleKeyboard(true) }),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val keyboard = KeyboardView(this, { send(it) }, { toggleKeyboard(false) })
        keyboard.visibility = View.GONE
        kb = keyboard
        root.addView(keyboard,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.2f))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val lp = { LinearLayout.LayoutParams(0, 220, 1f) }
        row.addView(mouseButton("ЛКМ", "l"), lp())
        row.addView(mouseButton("ПКМ", "r"), lp())
        row.addView(Button(this).apply {
            text = "⌨"
            setOnClickListener { toggleKeyboard() }
        }, LinearLayout.LayoutParams(0, 220, 0.6f))
        row.addView(Button(this).apply {
            text = "Выход"
            setOnClickListener { disconnect(); showDevices() }
        }, LinearLayout.LayoutParams(0, 220, 0.8f))
        root.addView(row)
        setContentView(root)
    }

    private fun disconnect() {
        try { socket?.close() } catch (_: IOException) {}
        socket = null; out = null; kb = null
    }

    override fun onDestroy() { disconnect(); io.shutdownNow(); super.onDestroy() }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
