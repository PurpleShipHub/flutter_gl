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
        try {
            Log.d(TAG, "Method called: ${call.method} with arguments: ${call.arguments}")
            
            when (call.method) {
                "getPlatformVersion" -> {
                    result.success("Android ${android.os.Build.VERSION.RELEASE}")
                    return
                }
                "initialize" -> {
                    // 안전한 타입 캐스팅
                    val args = call.arguments as? Map<*, *>
                    if (args == null) {
                        result.error("INVALID_ARGUMENTS", "Arguments cannot be null", null)
                        return
                    }

                    val options = args["options"] as? Map<*, *>
                    if (options == null) {
                        result.error("INVALID_OPTIONS", "Options cannot be null", null)
                        return
                    }

                    // 숫자 타입 안전하게 처리
                    val width = (options["width"] as? Number)?.toInt()
                    val height = (options["height"] as? Number)?.toInt()
                    val dpr = (options["dpr"] as? Number)?.toDouble() ?: 1.0

                    if (width == null || height == null) {
                        result.error("INVALID_DIMENSIONS", "Width and height must be valid numbers", null)
                        return
                    }

                    val glWidth = (width * dpr).toInt()
                    val glHeight = (height * dpr).toInt()

                    val entry = registry.createSurfaceTexture()
                    val textureID = entry.id().toInt()

                    val render = CustomRender(entry, glWidth, glHeight)
                    renders[textureID] = render

                    result.success(mapOf("textureId" to textureID))
                }
                "getEgl" -> {
                    val args = call.arguments as? Map<*, *>
                    val textureId = (args?.get("textureId") as? Number)?.toInt()
                    
                    if (textureId == null) {
                        result.error("INVALID_TEXTURE_ID", "TextureId must be a valid number", null)
                        return
                    }

                    val render = renders[textureId]
                    if (render == null) {
                        result.error("RENDER_NOT_FOUND", "No render found for textureId: $textureId", null)
                        return
                    }

                    result.success(render.getEgl())
                }
                "updateTexture" -> {
                    val args = call.arguments as? Map<*, *>
                    val textureId = (args?.get("textureId") as? Number)?.toInt()
                    val sourceTexture = (args?.get("sourceTexture") as? Number)?.toInt()
                    
                    if (textureId == null || sourceTexture == null) {
                        result.error("INVALID_PARAMETERS", "TextureId and sourceTexture must be valid numbers", null)
                        return
                    }

                    val render = renders[textureId]
                    if (render == null) {
                        result.error("RENDER_NOT_FOUND", "No render found for textureId: $textureId", null)
                        return
                    }

                    result.success(render.updateTexture(sourceTexture))
                }
                "dispose" -> {
                    val args = call.arguments as? Map<*, *>
                    val textureId = (args?.get("textureId") as? Number)?.toInt()
                    
                    if (textureId != null) {
                        renders[textureId]?.dispose()
                        renders.remove(textureId)
                    }
                    
                    result.success(null)
                }
                else -> {
                    result.notImplemented()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in ${call.method}: ${e.message}", e)
            result.error(
                "FLUTTER_GL_ERROR",
                "Error in ${call.method}: ${e.message}",
                e.stackTraceToString()
            )
        }
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_gl")
        channel.setMethodCallHandler(this)
        registry = flutterPluginBinding.textureRegistry
        context = flutterPluginBinding.applicationContext
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        try {
            renders.forEach { (_, render) -> render.dispose() }
            renders.clear()
            channel.setMethodCallHandler(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDetachedFromEngine: ${e.message}", e)
        }
    }
}
