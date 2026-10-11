package com.example.task_automator.helpers

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.StatFs
import java.io.File

object HardwareInspector {
    fun read(context: Context, modelDirectory: File): DeviceHardware {
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        return DeviceHardware(
            "${Build.MANUFACTURER} ${Build.MODEL}",
            if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE,
            Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList(), Process.is64Bit(),
            memory.totalMem, memory.availMem, StatFs(modelDirectory.absolutePath).availableBytes,
            Runtime.getRuntime().availableProcessors(), memory.lowMemory
        )
    }
}
