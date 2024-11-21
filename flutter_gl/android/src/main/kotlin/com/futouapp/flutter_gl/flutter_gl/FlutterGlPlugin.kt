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

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        try {
            channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_gl")
            channel.setMethodCallHandler(this)

            registry = flutterPluginBinding.textureRegistry
            context = flutterPluginBinding.applicationContext
            Log.d(TAG, "Plugin attached successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching plugin: ${e.message}", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onMethodCall(call: MethodCall, result: Result) {
        try {
            Log.d(TAG, "Method called: ${call.method} with arguments: ${call.arguments}")
            
            when (call.method) {
                "getPlatformVersion" -> {
                    result.success("Android ${android.os.Build.VERSION.RELEASE}")
                    return
                }
            }

            // 여기서 arguments가 null인지 체크
            if (call.arguments == null) {
                throw IllegalArgumentException("Arguments cannot be null for method: ${call.method}")
            }

            // Map으로 안전하게 캐스팅
            val arguments = try {
                when (val args = call.arguments) {
                    is Map<*, *> -> args.mapValues { (_, value) ->
                        when (value) {
                            is Number -> value
                            is Map<*, *> -> value.mapValues { it.value }
                            else -> value
                        }
                    }
                    else -> throw IllegalArgumentException("Arguments must be a Map")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing arguments: ${e.message}")
                throw IllegalArgumentException("Invalid arguments format: ${e.message}")
            }

            when (call.method) {
                "initialize" -> handleInitialize(arguments, result)
                "getEgl" -> handleGetEgl(arguments, result)
                "updateTexture" -> handleUpdateTexture(arguments, result)
                "dispose" -> handleDispose(arguments, result)
                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in method ${call.method}: ${e.message}", e)
            result.error(
                "FLUTTER_GL_ERROR",
                "Error in ${call.method}: ${e.message}",
                e.stackTraceToString()
            )
        }
    }

    private fun handleInitialize(args: Map<*, *>, result: Result) {
        try {
            Log.d(TAG, "Handling initialize with args: $args")
            
            val options = args["options"] as? Map<*, *> 
                ?: throw IllegalArgumentException("Options must be a Map")

            val width = when (val w = options["width"]) {
                is Number -> w.toInt()
                else -> throw IllegalArgumentException("Width must be a number, got: $w")
            }

            val height = when (val h = options["height"]) {
                is Number -> h.toInt()
                else -> throw IllegalArgumentException("Height must be a number, got: $h")
            }

            val dpr = when (val d = options["dpr"]) {
                is Number -> d.toDouble()
                null -> 1.0
                else -> throw IllegalArgumentException("DPR must be a number, got: $d")
            }

            val glWidth = (width * dpr).toInt()
            val glHeight = (height * dpr).toInt()

            Log.d(TAG, "Creating texture with dimensions: $glWidth x $glHeight")

            val entry = registry.createSurfaceTexture()
            val textureID = entry.id().toInt()

            val render = CustomRender(entry, glWidth, glHeight)
            renders[textureID] = render

            result.success(mapOf("textureId" to textureID))
        } catch (e: Exception) {
            Log.e(TAG, "Initialize error: ${e.message}", e)
            result.error("INIT_ERROR", e.message, e.stackTraceToString())
        }
    }

    private fun handleGetEgl(args: Map<*, *>, result: Result) {
        try {
            val textureId = when (val id = args["textureId"]) {
                is Number -> id.toInt()
                else -> throw IllegalArgumentException("TextureId must be a number, got: $id")
            }

            val render = renders[textureId] 
                ?: throw IllegalStateException("No render found for texture ID: $textureId")
            
            val eglResult = render.getEgl()
            result.success(eglResult)
        } catch (e: Exception) {
            Log.e(TAG, "GetEgl error: ${e.message}", e)
            result.error("EGL_ERROR", e.message, e.stackTraceToString())
        }
    }

    private fun handleUpdateTexture(args: Map<*, *>, result: Result) {
        try {
            val textureId = when (val id = args["textureId"]) {
                is Number -> id.toInt()
                else -> throw IllegalArgumentException("TextureId must be a number, got: $id")
            }

            val sourceTexture = when (val src = args["sourceTexture"]) {
                is Number -> src.toInt()
                else -> throw IllegalArgumentException("SourceTexture must be a number, got: $src")
            }

            val render = renders[textureId] 
                ?: throw IllegalStateException("No render found for texture ID: $textureId")
            
            val resp = render.updateTexture(sourceTexture)
            result.success(resp)
        } catch (e: Exception) {
            Log.e(TAG, "UpdateTexture error: ${e.message}", e)
            result.error("UPDATE_TEXTURE_ERROR", e.message, e.stackTraceToString())
        }
    }

    private fun handleDispose(args: Map<*, *>, result: Result) {
        try {
            val textureId = when (val id = args["textureId"]) {
                is Number -> id.toInt()
                null -> null
                else -> throw IllegalArgumentException("TextureId must be a number or null, got: $id")
            }

            if (textureId != null) {
                val render = renders[textureId]
                render?.dispose()
                renders.remove(textureId)
                Log.d(TAG, "Disposed render for textureId: $textureId")
            }

            result.success(null)
        } catch (e: Exception) {
            Log.e(TAG, "Dispose error: ${e.message}", e)
            result.error("DISPOSE_ERROR", e.message, e.stackTraceToString())
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        try {
            channel.setMethodCallHandler(null)
            renders.forEach { (id, render) ->
                try {
                    render.dispose()
                    Log.d(TAG, "Disposed render for textureId: $id")
                } catch (e: Exception) {
                    Log.e(TAG, "Error disposing render $id: ${e.message}", e)
                }
            }
            renders.clear()
            Log.d(TAG, "Plugin detached successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error detaching plugin: ${e.message}", e)
        }
    }
}
