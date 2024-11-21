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
            Log.d(TAG, "Method called: ${call.method}")
            
            // 먼저 arguments가 null이 아닌지 확인
            if (call.arguments == null && call.method != "getPlatformVersion") {
                throw IllegalArgumentException("Arguments cannot be null")
            }

            when (call.method) {
                "getPlatformVersion" -> {
                    result.success("Android ${android.os.Build.VERSION.RELEASE}")
                }
                "initialize" -> {
                    val args = call.arguments as? Map<String, Any> 
                        ?: throw IllegalArgumentException("Invalid arguments type for initialize")
                    handleInitialize(args, result)
                }
                "getEgl" -> {
                    val args = call.arguments as? Map<String, Any> 
                        ?: throw IllegalArgumentException("Invalid arguments type for getEgl")
                    handleGetEgl(args, result)
                }
                "updateTexture" -> {
                    val args = call.arguments as? Map<String, Any> 
                        ?: throw IllegalArgumentException("Invalid arguments type for updateTexture")
                    handleUpdateTexture(args, result)
                }
                "dispose" -> {
                    val args = call.arguments as? Map<String, Any> 
                        ?: throw IllegalArgumentException("Invalid arguments type for dispose")
                    handleDispose(args, result)
                }
                else -> {
                    result.notImplemented()
                }
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

    private fun handleInitialize(args: Map<String, Any>, result: Result) {
        try {
            Log.d(TAG, "Handling initialize with args: $args")
            
            val options = args["options"] as? Map<String, Any> 
                ?: throw IllegalArgumentException("Options must be a Map")

            // Null 안전성을 위해 기본값 제공
            val width = (options["width"] as? Number)?.toInt() 
                ?: throw IllegalArgumentException("Width is required and must be a number")
            val height = (options["height"] as? Number)?.toInt() 
                ?: throw IllegalArgumentException("Height is required and must be a number")
            val dpr = (options["dpr"] as? Number)?.toDouble() ?: 1.0

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

    private fun handleGetEgl(args: Map<String, Any>, result: Result) {
        try {
            val textureId = (args["textureId"] as? Number)?.toInt() 
                ?: throw IllegalArgumentException("TextureId must be a number")

            val render = renders[textureId] 
                ?: throw IllegalStateException("No render found for texture ID: $textureId")
            
            val eglResult = render.getEgl()
            result.success(eglResult)
        } catch (e: Exception) {
            Log.e(TAG, "GetEgl error: ${e.message}", e)
            result.error("EGL_ERROR", e.message, e.stackTraceToString())
        }
    }

    private fun handleUpdateTexture(args: Map<String, Any>, result: Result) {
        try {
            val textureId = (args["textureId"] as? Number)?.toInt() 
                ?: throw IllegalArgumentException("TextureId must be a number")
            val sourceTexture = (args["sourceTexture"] as? Number)?.toInt() 
                ?: throw IllegalArgumentException("SourceTexture must be a number")

            val render = renders[textureId] 
                ?: throw IllegalStateException("No render found for texture ID: $textureId")
            
            val resp = render.updateTexture(sourceTexture)
            result.success(resp)
        } catch (e: Exception) {
            Log.e(TAG, "UpdateTexture error: ${e.message}", e)
            result.error("UPDATE_TEXTURE_ERROR", e.message, e.stackTraceToString())
        }
    }

    private fun handleDispose(args: Map<String, Any>, result: Result) {
        try {
            val textureId = (args["textureId"] as? Number)?.toInt()

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
            // Clean up renders
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
