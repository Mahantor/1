package com.example

import android.app.Activity
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MainActivity
 * این اکتیویتی به صورت بدون رابط کاربری (Headless - Translucent) تعریف شده است.
 * کاربر با لمس آیکون برنامه مستقیماً عملیات روت تترینگ را در پس‌زمینه اجرا میکند.
 * پس از اتمام فرآیند، نتیجه را با یک Toast نشان داده و سریعاً بسته (finish) میشود.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // اجرای موازی و پس‌زمینه با Coroutines
        CoroutineScope(Dispatchers.Main).launch {
            try {
                toggleUsbTethering()
            } catch (e: Exception) {
                showToast("خطا در اجرا: ${e.message}")
            } finally {
                // اتمام سریع اکتیویتی به منظور جلوگیری از لود صفحه سفید رنگ
                finish()
            }
        }
    }

    private suspend fun toggleUsbTethering() {
        // ۱. بررسی دسترسی روت
        val hasRoot = withContext(Dispatchers.IO) {
            RootHelper.checkRootPermission()
        }
        if (!hasRoot) {
            showToast("فقط روی گوشی روت شده کار میکند")
            return
        }

        // ۲. بررسی اتصال فیزیکی کابل USB
        val isUsbConnected = checkUsbConnectedState()
        if (!isUsbConnected) {
            showToast("لطفاً کابل USB را وصل کن")
            return
        }

        // ۳. تشخیص آخرین وضعیت فعال بودن تترینگ فعلی
        val isCurrentlyActive = withContext(Dispatchers.IO) {
            detectTetheringState()
        }

        val targetState = !isCurrentlyActive

        // ۴. اقدام به تغییر وضعیت با اولویت‌ها
        val success = withContext(Dispatchers.IO) {
            performToggleOperation(targetState)
        }

        if (success) {
            val statusText = if (targetState) "روشن (فعال)" else "خاموش (غیرفعال)"
            showToast("وضعیت USB Tethering با موفقیت تغییر کرد به: $statusText")
        } else {
            showToast("تغییر حاصل نشد؛ لطفاً از صحت دسترسی روت و اتصال لایه سخت‌افزار مطمئن شوید.")
        }
    }

    /**
     * بررسی وضعیت کابل یواس‌بی از طریق رجیستری IntentFilter
     */
    private fun checkUsbConnectedState(): Boolean {
        return try {
            val filter = IntentFilter("android.hardware.usb.action.USB_STATE")
            val intent = registerReceiver(null, filter)
            intent?.getBooleanExtra("connected", false) ?: false
        } catch (e: Exception) {
            // در حالت شکست سیستمی، فرض میکنیم کابل وصل است تا دستورات شل را تست کنیم
            true
        }
    }

    /**
     * شبیه‌سازی دقیق دستور تشخیص روشن/خاموش بودن تترینگ:
     * su -c "ip link show | grep -E 'usb0|rndis0|usb[0-9]+'"
     */
    private fun detectTetheringState(): Boolean {
        val result = RootHelper.runAsRoot("ip link show")
        val output = result.stdout
        return output.contains("usb0") || 
               output.contains("rndis0") || 
               Regex("usb[0-9]+").containsMatchIn(output)
    }

    /**
     * تغییر وضعیت با سه فاز اولویتی بر اساس اندروید ۸ تا ۱۴
     */
    private suspend fun performToggleOperation(targetState: Boolean): Boolean {
        val value = if (targetState) 1 else 0

        // روش اول (اولویت اول) – امتحانات کدهای تراکنشی
        val codes = listOf(39, 41, 45, 48, 53, 60)
        for (code in codes) {
            val cmd = "service call connectivity $code i32 $value"
            RootHelper.runAsRoot(cmd)
            
            // زمان دادن به سیستم‌عامل برای بالا آمدن اینترفیس
            delay(1000)
            if (detectTetheringState() == targetState) {
                return true
            }
        }

        // روش دوم (اولویت دوم) – svc usb
        val func = if (targetState) "rndis" else "mtp"
        RootHelper.runAsRoot("svc usb setFunctions $func")
        delay(1200)
        if (detectTetheringState() == targetState) {
            return true
        }

        // روش سوم (اولویت سوم) – echo به دیوایس مجازی هسته لینوکس
        if (targetState) {
            RootHelper.runAsRoot("echo 1 > /sys/devices/virtual/android_usb/android0/enable")
            RootHelper.runAsRoot("echo rndis > /sys/devices/virtual/android_usb/android0/functions")
        } else {
            RootHelper.runAsRoot("echo 0 > /sys/devices/virtual/android_usb/android0/enable")
        }
        delay(1200)
        return detectTetheringState() == targetState
    }

    private fun showToast(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
    }
}
