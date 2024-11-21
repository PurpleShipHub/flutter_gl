package com.futouapp.flutter_gl.flutter_gl

import android.content.Context
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.view.TextureRegistry

class FlutterGlPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var registry: TextureRegistry
    private lateinit var context: Context

    private var renders = mutableMapOf<Int, CustomRender>()
    private val TAG = "FlutterGlPlugin"

    override fun onMethodCall(call: MethodCall, result: Result) {
        Log.d(TAG, """
            ====== Method Call Debug ======
            Method: ${call.method}
            Arguments: ${call.arguments}
            Arguments Type: ${call.arguments?.javaClass}
            Arguments Content: ${call.arguments?.toString()}
            =============================
        """.trimIndent())

        try {
            when (call.method) {
                "getPlatformVersion" -> {
                    Log.d(TAG, "Handling getPlatformVersion")
                    result.success("Android ${android.os.Build.VERSION.RELEASE}")
                }
                "initialize" -> {
                    Log.d(TAG, "Handling initialize")
                    val args = (call.arguments as Map<*, *>).mapKeys { it.key as String }
                    val options = (args["options"] as Map<*, *>?)!!.mapKeys { it.key as String }
                    
                    Log.d(TAG, """
                        Initialize Details:
                        Args: $args
                        Options: $options
                        Width: ${options["width"]}
                        Height: ${options["height"]}
                        DPR: ${options["dpr"]}
                    """.trimIndent())

                    val glWidth = ((options["width"] as Int) * (options["dpr"] as Double)).toInt()
                    val glHeight = ((options["height"] as Int) * (options["dpr"] as Double)).toInt()

                    Log.d(TAG, "Calculated dimensions - Width: $glWidth, Height: $glHeight")

                    val entry = registry.createSurfaceTexture()
                    val textureID = entry.id().toInt()
                    
                    Log.d(TAG, "Created texture with ID: $textureID")

                    val render = CustomRender(entry, glWidth, glHeight)
                    renders[textureID] = render

                    result.success(mapOf("textureId" to textureID))
                }
                "getEgl" -> {
                    Log.d(TAG, "Handling getEgl")
                    val args = (call.arguments as Map<*, *>).mapKeys { it.key as String }
                    val textureId = args["textureId"] as Int
                    
                    Log.d(TAG, "GetEgl for textureId: $textureId")

                    val render = this.renders[textureId]
                    val eglResult = render?.getEgl()
                    
                    Log.d(TAG, "EGL Result: $eglResult")
                    result.success(eglResult)
                }
                "updateTexture" -> {
                    Log.d(TAG, "Handling updateTexture")
                    val args = (call.arguments as Map<*, *>).mapKeys { it.key as String }
                    val textureId = args["textureId"] as Int
                    val sourceTexture = args["sourceTexture"] as Int
                    
                    Log.d(TAG, """
                        UpdateTexture Details:
                        TextureId: $textureId
                        SourceTexture: $sourceTexture
                    """.trimIndent())

                    val resp = this.renders[textureId]?.updateTexture(sourceTexture)
                    Log.d(TAG, "UpdateTexture Response: $resp")
                    result.success(resp)
                }
                "dispose" -> {
                    Log.d(TAG, "Handling dispose")
                    val args = (call.arguments as Map<*, *>).mapKeys { it.key as String }
                    val textureId = args["textureId"] as? Int
                    
                    Log.d(TAG, "Disposing textureId: $textureId")

                    if (textureId != null) {
                        val render = this.renders[textureId]
                        render?.dispose()
                        this.renders.remove(textureId)
                        Log.d(TAG, "Successfully disposed texture: $textureId")
                    }

                    result.success(null)
                }
                else -> {
                    Log.d(TAG, "Method not implemented: ${call.method}")
                    result.notImplemented()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, """
                Error in ${call.method}:
                Error Type: ${e.javaClass.simpleName}
                Message: ${e.message}
                Stack Trace: ${e.stackTraceToString()}
            """.trimIndent())
            throw e
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "Attaching to engine")
        channel = MethodChannel(binding.binaryMessenger, "flutter_gl")
        channel.setMethodCallHandler(this)
        registry = binding.textureRegistry
        context = binding.applicationContext
        Log.d(TAG, "Successfully attached to engine")
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "Detaching from engine")
        channel.setMethodCallHandler(null)
        Log.d(TAG, "Successfully detached from engine")
    }
}
