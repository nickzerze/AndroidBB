package com.example.androidbb

import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import android.widget.Spinner
import android.widget.ArrayAdapter
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import kotlin.concurrent.thread
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private var SERVER_URL = ""
    private lateinit var imageView: ImageView
    private lateinit var overlay: BoundingBoxOverlay
    private lateinit var trackerSpinner: Spinner

    private var lastServerFrameTimestamp = 0.0
    private var lastPhoneReceivedTimestamp = 0.0

    private var selectedTracker = "CSRT"   // Default tracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        overlay = findViewById(R.id.boundingBoxOverlay)
        trackerSpinner = findViewById(R.id.trackerSpinner)

        // ------------------ TRACKER SELECTOR ------------------
        val trackers = listOf("CSRT", "KCF", "MOSSE", "MIL", "TLD", "MEDIAN")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, trackers)
        trackerSpinner.adapter = adapter

        trackerSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                selectedTracker = trackers[position]
                println("Tracker selected: $selectedTracker")
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
        // ------------------------------------------------------

        // When user draws a bounding box:
        overlay.onBoxSelected = { rect ->
            val normLeft = rect.left / imageView.width
            val normTop = rect.top / imageView.height
            val normWidth = rect.width() / imageView.width
            val normHeight = rect.height() / imageView.height

            val json = JSONObject()
            json.put("x", normLeft)
            json.put("y", normTop)
            json.put("width", normWidth)
            json.put("height", normHeight)
            json.put("client_send_ts", System.currentTimeMillis() / 1000.0)
            json.put("server_frame_ts", lastServerFrameTimestamp)
            json.put("client_recv_ts", lastPhoneReceivedTimestamp)

            // ------------- ADD TRACKER NAME ---------------
            json.put("tracker", selectedTracker)
            // ----------------------------------------------

            sendBoundingBoxToServer(json.toString())
        }

        // Auto-find server IP (your existing code)
        thread {
            val ip = findServerIP()
            if (ip == null) {
                println("❌ Could not find server on hotspot.")
                return@thread
            }

            SERVER_URL = "http://$ip:10000"
            println("SERVER FOUND AT $SERVER_URL")

            runOnUiThread { startReceivingFrames() }
        }
    }

    private fun sendBoundingBoxToServer(json: String) {
        val client = OkHttpClient()
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$SERVER_URL/bbox")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    runOnUiThread { overlay.clearBox() }
                }
                response.close()
            }
        })
    }

    private fun startReceivingFrames() {
        thread {
            while (true) {
                try {
                    val url = URL("$SERVER_URL/frame")
                    val connection = url.openConnection()
                    connection.connect()

                    val serverTsHeader = connection.getHeaderField("X-Server-Timestamp")
                    val serverFrameTimestamp = serverTsHeader?.toDoubleOrNull() ?: 0.0
                    val phoneReceivedTimestamp = System.currentTimeMillis() / 1000.0

                    lastServerFrameTimestamp = serverFrameTimestamp
                    lastPhoneReceivedTimestamp = phoneReceivedTimestamp

                    val bmp = BitmapFactory.decodeStream(connection.getInputStream())

                    runOnUiThread { imageView.setImageBitmap(bmp) }
                    Thread.sleep(100)

                } catch (e: Exception) {
                    println("❌ Error fetching frame:")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getSubnet(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (iface in interfaces) {
                val ifaceName = iface.displayName.lowercase()

                if (!ifaceName.contains("wlan") &&
                    !ifaceName.contains("ap") &&
                    !ifaceName.contains("wifi")) continue

                for (addr in iface.inetAddresses) {
                    val ip = addr.hostAddress ?: continue
                    if (ip.contains(":")) continue
                    val parts = ip.split(".")
                    if (parts.size == 4) return "${parts[0]}.${parts[1]}.${parts[2]}."
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return null
    }

    private fun findServerIP(): String? {
        val base = getSubnet() ?: return null
        val port = 10000

        val executor = Executors.newFixedThreadPool(50)
        val result = arrayOfNulls<String>(1)

        for (i in 2..254) {
            val testIp = "$base$i"
            executor.execute {
                try {
                    val url = URL("http://$testIp:$port/ping")
                    val conn = url.openConnection()
                    conn.connectTimeout = 50
                    conn.readTimeout = 50
                    conn.getInputStream().close()
                    synchronized(result) {
                        if (result[0] == null) result[0] = testIp
                    }
                } catch (_: Exception) {}
            }
        }

        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)
        return result[0]
    }
}
