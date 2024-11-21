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

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.e(TAG, "Attaching to engine")
        channel = MethodChannel(binding.binaryMessenger, "flutter_gl")
        channel.setMethodCallHandler(this)
        registry = binding.textureRegistry
        context = binding.applicationContext
        Log.e(TAG, "Successfully attached to engine")
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        // 가장 먼저 로그 출력
        Log.e(TAG, """
            ======== Method Call Debug ========
            Method: ${call.method}
            Raw Arguments: ${call.arguments}
            Arguments Type: ${call.arguments?.javaClass}
            ================================
        """.trimIndent())

        try {
            // 40번 라인 이전에 추가 로그
            if (call.arguments != null) {
                Log.e(TAG, """
                    ======== Arguments Detail ========
                    Arguments Keys: ${(call.arguments as? Map<*, *>)?.keys}
                    Arguments Values: ${(call.arguments as? Map<*, *>)?.values}
                    ================================
                """.trimIndent())
            }

            when (call.method) {
                "getPlatformVersion" -> {
                    Log.e(TAG, "Handling getPlatformVersion")
                    result.success("Android ${android.os.Build.VERSION.RELEASE}")
                }
                "initialize" -> {
                    Log.e(TAG, "Handling initialize")
                    val args = (call.arguments as Map<*, *>).mapKeys { it.key as String }
                    val options = (args["options"] as Map<*, *>?)!!.mapKeys { it.key as String }
                    
                    Log.e(TAG, """
                        Initialize Details:
                        Args: $args
                        Options: $options
                        Width: ${options["width"]}
                        Height: ${options["height"]}
                        DPR: ${options["dpr"]}
                    """.trimIndent())

                    val glWidth = ((options["width"] as Int) * (options["dpr"] as Double)).toInt()
                    val glHeight = ((options["height"] as Int) * (options["dpr"] as Double)).toInt()

                    Log.e(TAG, "Calculated dimensions - Width: $glWidth, Height: $glHeight")

                    val entry = registry.createSurfaceTexture()
                    val textureID = entry.id().toInt()
                    
                    Log.e(TAG, "Created texture with ID: $textureID")

                    val render = CustomRender(entry, glWidth, glHeight)
                    renders[textureID] = render

                    result.success(mapOf("textureId" to textureID))
                }
                "getEgl" -> {
                    Log.e(TAG, "Handling getEgl")
                    val args = (call.arguments as Map<*, *>).mapKeys { it.key as String }
                    val textureId = args["textureId"] as Int
                    
                    Log.e(TAG, "GetEgl for textureId: $textureId")

                    val render = this.renders[textureId]
                    val eglResult = render?.getEgl()
                    
                    Log.e(TAG, "EGL Result: $eglResult")
                    result.success(eglResult)
                }
                "updateTexture" -> {
                    Log.e(TAG, "Handling updateTexture")
                    val args = (call.arguments as Map<*, *>).mapKeys { it.key as String }
                    val textureId = args["textureId"] as Int
                    val sourceTexture = args["sourceTexture"] as Int
                    
                    Log.e(TAG, """
                        UpdateTexture Details:
                        TextureId: $textureId
                        SourceTexture: $sourceTexture
                    """.trimIndent())

                    val resp = this.renders[textureId]?.updateTexture(sourceTexture)
                    Log.e(TAG, "UpdateTexture Response: $resp")
                    result.success(resp)
                }
                "dispose" -> {
                    Log.e(TAG, "Handling dispose")
                    val args = (call.arguments as Map<*, *>).mapKeys { it.key as String }
                    val textureId = args["textureId"] as? Int
                    
                    Log.e(TAG, "Disposing textureId: $textureId")

                    if (textureId != null) {
                        val render = this.renders[textureId]
                        render?.dispose()
                        this.renders.remove(textureId)
                        Log.e(TAG, "Successfully disposed texture: $textureId")
                    }

                    result.success(null)
                }
                else -> {
                    Log.e(TAG, "Method not implemented: ${call.method}")
                    result.notImplemented()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, """
                ======== Error Detail ========
                Error in method: ${call.method}
                Error type: ${e.javaClass.simpleName}
                Error message: ${e.message}
                Stack trace: ${e.stackTraceToString()}
                ============================
            """.trimIndent())
            throw e
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.e(TAG, "Detaching from engine")
        channel.setMethodCallHandler(null)
        renders.forEach { (id, render) ->
            try {
                render.dispose()
                Log.e(TAG, "Disposed render for textureId: $id")
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing render $id: ${e.message}")
            }
        }
        renders.clear()
        Log.e(TAG, "Successfully detached from engine")
    }
}
