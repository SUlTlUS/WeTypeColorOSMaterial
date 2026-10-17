package dev.coloros.wetype.material

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.TimeUnit

class ModuleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(ModuleFramework)
    }
}

/** A configured scope is reported separately from whether a hook process has loaded. */
internal object ModuleFramework : XposedServiceHelper.OnServiceListener {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var service: XposedService? = null
    var status by mutableStateOf("未连接")
        private set

    override fun onServiceBind(service: XposedService) {
        this.service = service
        refresh()
    }

    override fun onServiceDied(service: XposedService) {
        if (this.service === service) this.service = null
        refresh()
    }

    fun refresh() {
        val value = runCatching {
            when {
                service == null -> "未连接"
                service!!.scope.contains("com.tencent.wetype") -> "作用域已配置"
                else -> "未配置作用域"
            }
        }.getOrDefault("连接不可用")
        main.post { status = value }
    }

    fun configure(context: Context) {
        val current = service
        if (current != null) {
            runCatching {
                current.requestScope(listOf("com.tencent.wetype"), object : XposedService.OnScopeEventListener {
                    override fun onScopeRequestApproved(scope: List<String>) { refresh() }
                    override fun onScopeRequestFailed(message: String) { toast(context, "作用域请求失败：$message") }
                })
            }.onFailure { toast(context, "无法请求作用域，请在 LSPosed 中勾选微信输入法") }
            return
        }
        runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage("org.lsposed.manager")
                ?: context.packageManager.getLaunchIntentForPackage("com.android.shell")
                ?: error("manager unavailable")
            intent.categories?.toList()?.forEach(intent::removeCategory)
            intent.addCategory("org.lsposed.manager.LAUNCH_MANAGER")
            intent.data = Uri.parse("module://${context.packageName}:${android.os.Process.myUid() / 100000}")
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { toast(context, "请在 LSPosed 中启用模块，并勾选微信输入法") }
    }

    fun restart(context: Context) {
        Thread {
            val result = runCatching {
                // Only the keyboard process. Android reconnects the selected IME service.
                val process = ProcessBuilder("su", "-c", "killall com.tencent.wetype:hld")
                    .redirectOutput(java.io.File("/dev/null"))
                    .redirectError(java.io.File("/dev/null")).start()
                if (!process.waitFor(8, TimeUnit.SECONDS)) {
                    process.destroy()
                    error("timeout")
                }
                check(process.exitValue() == 0)
            }
            toast(context, if (result.isSuccess) "已重启微信输入法进程" else "未能重启：请确认 Root 授权及输入法运行状态")
        }.start()
    }

    private fun toast(context: Context, message: String) {
        main.post { Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show() }
    }
}
