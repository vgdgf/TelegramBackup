package com.backup.telegram.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * أداة مساعدة خفيفة للتحقق المتزامن (synchronous) من حالة الشبكة الحالية.
 * تُستخدم من أي مكان يحتاج قراءة فورية للاتصال بدون Callback.
 */
object NetworkUtil {

    fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * يتحقق إن كانت الشبكة مناسبة للرفع مع مراعاة إعداد "WiFi فقط".
     */
    fun isSuitableForUpload(context: Context, wifiOnly: Boolean): Boolean {
        if (!isConnected(context)) return false
        return if (wifiOnly) isOnWifi(context) else true
    }
}
