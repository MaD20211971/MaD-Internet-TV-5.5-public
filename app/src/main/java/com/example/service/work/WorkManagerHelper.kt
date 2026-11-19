package com.example.service.work

import android.content.Context
import android.util.Log

object WorkManagerHelper {
    fun scheduleBackgroundTasks(context: Context) {
        Log.d("WorkManagerHelper", "Background task scheduler initialized for context: ${context.packageName}")
    }
}
