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


class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var overlay: BoundingBoxOverlay

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

            sendBoundingBoxToServer(json.toString())
        }

        startReceivingFrames()
    }

    private fun sendBoundingBoxToServer(json: String) {
        val client = OkHttpClient()
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
        //#.url("http://192.168.1.20:5000/bbox")
            .url("http://192.168.1.60:5000/bbox")
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
                    val url = URL("http://192.168.1.60:5000/frame")
                    val bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream())

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
}
