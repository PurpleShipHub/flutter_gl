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
    private val TAG = "FLUTTER_GL_DEBUG"

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        System.err.println("$TAG: Attaching to engine")
        channel = MethodChannel(binding.binaryMessenger, "flutter_gl")
        channel.setMethodCallHandler(this)
        registry = binding.textureRegistry
        context = binding.applicationContext
        System.err.println("$TAG: Successfully attached to engine")
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        System.err.println("========= Method Call Start =========")
        System.err.println("Method: ${call.method}")
        System.err.println("Arguments: ${call.arguments}")
        System.err.println("Arguments Type: ${call.arguments?.javaClass}")
        System.err.println("=================================")

        try {
            when (call.method) {
                "getPlatformVersion" -> {
                    System.err.println("$TAG: Handling getPlatformVersion")
                    result.success("Android ${android.os.Build.VERSION.RELEASE}")
                }
                "initialize" -> {
                    System.err.println("$TAG: Handling initialize")
                    val args = (call.arguments as Map<*, *>).mapKeys { it.key as String }
                    val options = (args["options"] as Map<*, *>?)!!.mapKeys { it.key as String }
                    
                    System.err.println("""
                        $TAG: Initialize Details:
                        Args: $args
                        Options: $options
                        Width: ${options["width"]}
                        Height: ${options["height"]}
                        DPR: ${options["dpr"]}
                    """.trimIndent())

                    val glWidth = ((options["width"] as Int) * (options["dpr"] as Double)).toInt()
                    val glHeight = ((options["height"] as Int) * (options["dpr"] as Double)).toInt()

                    System.err.println("$TAG: Calculated dimensions - Width: $glWidth, Height: $glHeight")

                    val entry = registry.createSurfaceTexture()
                    val textureID = entry.id().toInt()
                    
                    System.err.println("$TAG: Created texture with ID: $textureID")

                    val render = CustomRender(entry, glWidth, glHeight)
                    renders[textureID] = render

                    result.success(mapOf("textureId" to textureID))
                }
                "getEgl" -> {
                    System.err.println("$TAG: Handling getEgl")
                    val args = (call.arguments as Map<*, *>).mapKeys { it.key as String }
                    val textureId = args["textureId"] as Int
                    
                    System.err.println("$TAG: GetEgl for textureId: $textureId")

                    val render = this.renders[textureId]
                    val eglResult = render?.getEgl()
                    
                    System.err.println("$TAG: EGL Result: $eglResult")
                    result.success(eglResult)
                }
                "updateTexture" -> {
                    System.err.println("$TAG: Handling updateTexture")
                    val args = (call.arguments as Map<*, *>).mapKeys { it.key as String }
                    val textureId = args["textureId"] as Int
                    val sourceTexture = args["sourceTexture"] as Int
                    
                    System.err.println("""
                        $TAG: UpdateTexture Details:
                        TextureId: $textureId
                        SourceTexture: $sourceTexture
                    """.trimIndent())

                    val resp = this.renders[textureId]?.updateTexture(sourceTexture)
                    System.err.println("$TAG: UpdateTexture Response: $resp")
                    result.success(resp)
                }
                "dispose" -> {
                    System.err.println("$TAG: Handling dispose")
                    val args = (call.arguments as Map<*, *>).mapKeys { it.key as String }
                    val textureId = args["textureId"] as? Int
                    
                    System.err.println("$TAG: Disposing textureId: $textureId")

                    if (textureId != null) {
                        val render = this.renders[textureId]
                        render?.dispose()
                        this.renders.remove(textureId)
                        System.err.println("$TAG: Successfully disposed texture: $textureId")
                    }

                    result.success(null)
                }
                else -> {
                    System.err.println("$TAG: Method not implemented: ${call.method}")
                    result.notImplemented()
                }
            }
        } catch (e: Exception) {
            System.err.println("""
                ========= Error Detail =========
                Error in method: ${call.method}
                Error type: ${e.javaClass.simpleName}
                Error message: ${e.message}
                Stack trace: ${e.stackTraceToString()}
                ==============================
            """.trimIndent())
            throw e
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        System.err.println("$TAG: Detaching from engine")
        channel.setMethodCallHandler(null)
        renders.forEach { (id, render) ->
            try {
                render.dispose()
                System.err.println("$TAG: Disposed render for textureId: $id")
            } catch (e: Exception) {
                System.err.println("$TAG: Error disposing render $id: ${e.message}")
            }
        }
        renders.clear()
        System.err.println("$TAG: Successfully detached from engine")
    }
}
