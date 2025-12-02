package com.example.androidbb

import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import kotlin.concurrent.thread
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit



class MainActivity : AppCompatActivity() {

    private var SERVER_URL = ""
    private lateinit var imageView: ImageView
    private lateinit var overlay: BoundingBoxOverlay
    private var lastServerFrameTimestamp = 0.0
    private var lastPhoneReceivedTimestamp = 0.0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        overlay = findViewById(R.id.boundingBoxOverlay)

        overlay.onBoxSelected = { rect ->
            val viewWidth = imageView.width.toFloat()
            val viewHeight = imageView.height.toFloat()

//            val normalizedX = rect.left / viewWidth
//            val normalizedY = rect.top / viewHeight
//            val normalizedW = rect.width() / viewWidth
//            val normalizedH = rect.height() / viewHeight
//
//            val json = JSONObject()
//            json.put("x", normalizedX)
//            json.put("y", normalizedY)
//            json.put("width", normalizedW)
//            json.put("height", normalizedH)

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


            sendBoundingBoxToServer(json.toString())
        }

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
            //.url("http://192.168.108.200:5000/bbox")
            .url("$SERVER_URL/bbox")
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    runOnUiThread {
                        overlay.clearBox()  // ✅ this clears the red box
                    }
                }
                response.close()
            }
        })
    }

    private fun startReceivingFrames() {
        thread {
            while (true) {
                try {
                    //val url = URL("http://192.168.108.200:5000/frame")
                    val url = URL("$SERVER_URL/frame")

                    // --- OPEN CONNECTION (needed to read headers) ---
                    val connection = url.openConnection()
                    connection.connect()

                    // --- READ SERVER TIMESTAMP HEADER ---
                    val serverTsHeader = connection.getHeaderField("X-Server-Timestamp")
                    val serverFrameTimestamp = serverTsHeader?.toDoubleOrNull() ?: 0.0

                    // --- PHONE RECEIVED TIMESTAMP ---
                    val phoneReceivedTimestamp = System.currentTimeMillis() / 1000.0

                    // --- SAVE THEM TO GLOBAL VARIABLES ---
                    lastServerFrameTimestamp = serverFrameTimestamp
                    lastPhoneReceivedTimestamp = phoneReceivedTimestamp

                    // --- DECODE IMAGE ---
                    val bmp = BitmapFactory.decodeStream(connection.getInputStream())


                    println("Frame received from server")  // This won't be reached if decode fails

                    runOnUiThread {
                        imageView.setImageBitmap(bmp)
                    }
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
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()

            for (iface in interfaces) {
                val ifaceName = iface.displayName.lowercase()

                // We only care about hotspot / wifi AP interfaces:
                if (!ifaceName.contains("wlan") &&
                    !ifaceName.contains("ap") &&
                    !ifaceName.contains("wifi")) continue

                for (addr in iface.inetAddresses) {
                    val ip = addr.hostAddress ?: continue

                    // skip IPv6
                    if (ip.contains(":")) continue

                    // Only IPv4 remains
                    val parts = ip.split(".")
                    if (parts.size == 4) {
                        return "${parts[0]}.${parts[1]}.${parts[2]}."
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
