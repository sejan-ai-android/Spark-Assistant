package com.example.spark.engine

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HardwareEngine(private val context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var cameraIdWithFlash: String? = null

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    init {
        try {
            cameraManager?.let { cm ->
                for (id in cm.cameraIdList) {
                    val characteristics = cm.getCameraCharacteristics(id)
                    val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        cameraIdWithFlash = id
                        break
                    }
                }
                if (cameraIdWithFlash == null && cm.cameraIdList.isNotEmpty()) {
                    cameraIdWithFlash = cm.cameraIdList[0]
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                            super.onTorchModeChanged(cameraId, enabled)
                            if (cameraId == cameraIdWithFlash) {
                                _isFlashlightOn.value = enabled
                            }
                        }
                    }, null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleFlashlight(desiredState: Boolean? = null): Result<Boolean> {
        val cm = cameraManager ?: return Result.failure(IllegalStateException("Camera service unavailable"))
        val camId = cameraIdWithFlash ?: return Result.failure(IllegalStateException("No camera with flash found"))

        return try {
            val targetState = desiredState ?: !_isFlashlightOn.value
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm.setTorchMode(camId, targetState)
                _isFlashlightOn.value = targetState
                Result.success(targetState)
            } else {
                Result.failure(UnsupportedOperationException("Torch mode requires Android 6.0+"))
            }
        } catch (e: CameraAccessException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openSettings(type: String): Result<String> {
        val action = when (type.lowercase().trim()) {
            "wifi", "wifi_settings", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth", "bluetooth_settings" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "location", "location_settings", "gps" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "hotspot", "hotspot_settings", "tethering" -> {
                "android.settings.TETHER_SETTINGS"
            }
            "display", "display_settings" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume", "sound_settings" -> Settings.ACTION_SOUND_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }

        return try {
            val intent = Intent(action).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Result.success("Opened $type settings")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
