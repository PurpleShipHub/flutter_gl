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
    companion object {
        private const val TAG = "FlutterGlPlugin"
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "Attaching FlutterGl to engine")
        channel = MethodChannel(binding.binaryMessenger, "flutter_gl")
        channel.setMethodCallHandler(this)
        registry = binding.textureRegistry
        context = binding.applicationContext
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        Log.d(TAG, "Method called: ${call.method}")
        Log.d(TAG, "Arguments: ${call.arguments}")

        try {
            when (call.method) {
                "getPlatformVersion" -> {
                    Log.d(TAG, "Getting platform version")
                    result.success("Android ${android.os.Build.VERSION.RELEASE}")
                }
                "initialize" -> {
                    Log.d(TAG, "Initializing FlutterGl")
                    val args = call.arguments as? Map<*, *>
                    Log.d(TAG, "Initialize args: $args")
                    
                    val options = args?.get("options") as? Map<*, *>
                    Log.d(TAG, "Initialize options: $options")

                    val width = options?.get("width") as? Int
                    val height = options?.get("height") as? Int
                    val dpr = (options?.get("dpr") as? Double) ?: 1.0

                    Log.d(TAG, "Width: $width, Height: $height, DPR: $dpr")

                    if (width == null || height == null) {
                        throw IllegalArgumentException("Width and height must not be null")
                    }

                    val glWidth = (width * dpr).toInt()
                    val glHeight = (height * dpr).toInt()

                    val entry = registry.createSurfaceTexture()
                    val textureID = entry.id().toInt()
                    
                    Log.d(TAG, "Created texture with ID: $textureID")

                    val render = CustomRender(entry, glWidth, glHeight)
                    renders[textureID] = render

                    result.success(mapOf("textureId" to textureID))
                }
                "getEgl" -> {
                    Log.d(TAG, "Getting EGL")
                    val args = call.arguments as? Map<*, *>
                    val textureId = args?.get("textureId") as? Int
                    Log.d(TAG, "GetEgl for textureId: $textureId")

                    if (textureId == null) {
                        throw IllegalArgumentException("TextureId must not be null")
                    }

                    val render = renders[textureId]
                    val eglResult = render?.getEgl()
                    
                    Log.d(TAG, "EGL Result: $eglResult")
                    result.success(eglResult)
                }
                "updateTexture" -> {
                    Log.d(TAG, "Updating texture")
                    val args = call.arguments as? Map<*, *>
                    val textureId = args?.get("textureId") as? Int
                    val sourceTexture = args?.get("sourceTexture") as? Int
                    
                    Log.d(TAG, "TextureId: $textureId, SourceTexture: $sourceTexture")

                    if (textureId == null || sourceTexture == null) {
                        throw IllegalArgumentException("TextureId and sourceTexture must not be null")
                    }

                    val render = renders[textureId]
                    val resp = render?.updateTexture(sourceTexture)
                    Log.d(TAG, "Update response: $resp")
                    result.success(resp)
                }
                "dispose" -> {
                    Log.d(TAG, "Disposing")
                    val args = call.arguments as? Map<*, *>
                    val textureId = args?.get("textureId") as? Int
                    Log.d(TAG, "Disposing textureId: $textureId")

                    if (textureId != null) {
                        renders[textureId]?.dispose()
                        renders.remove(textureId)
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
            Log.e(TAG, "Error in ${call.method}: ${e.message}", e)
            result.error(
                "FLUTTER_GL_ERROR",
                "Error in ${call.method}: ${e.message}",
                e.stackTraceToString()
            )
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "Detaching from engine")
        channel.setMethodCallHandler(null)
        renders.forEach { (id, render) ->
            try {
                render.dispose()
                Log.d(TAG, "Disposed render for textureId: $id")
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing render $id: ${e.message}")
            }
        }
        renders.clear()
    }
}
