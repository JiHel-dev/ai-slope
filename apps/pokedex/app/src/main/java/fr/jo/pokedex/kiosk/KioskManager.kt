package fr.jo.pokedex.kiosk

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the app pinned in the foreground.
 *
 * With device owner rights (provisioned once with `adb shell dpm set-device-owner`) the app uses
 * managed Lock Task Mode and becomes the home screen, so it also comes back after a reboot.
 * Without them it falls back to standard screen pinning, which the user can leave.
 */
class KioskManager(context: Context) {

    enum class Mode { MANAGED, PINNING }

    private val appContext = context.applicationContext
    private val packageName = appContext.packageName
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
    private val activityManager = appContext.getSystemService(ActivityManager::class.java)
    private val admin = ComponentName(appContext, PokedexAdminReceiver::class.java)
    private val homeAlias = ComponentName(packageName, "$packageName.HomeAlias")

    private val _maintenance = MutableStateFlow(false)

    /** True while an adult has temporarily unlocked the device from the parental screen. */
    val maintenance: StateFlow<Boolean> = _maintenance.asStateFlow()

    val isDeviceOwner: Boolean get() = dpm.isDeviceOwnerApp(packageName)

    val mode: Mode get() = if (isDeviceOwner) Mode.MANAGED else Mode.PINNING

    val isLocked: Boolean get() = activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    fun applyPolicies() {
        if (!isDeviceOwner) return
        runCatching {
            dpm.setLockTaskPackages(admin, arrayOf(packageName))
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS)
            setHomeAliasEnabled(true)
            dpm.addPersistentPreferredActivity(admin, homeFilter(), homeAlias)
            dpm.setKeyguardDisabled(admin, true)
            dpm.setStatusBarDisabled(admin, true)
            dpm.setPermissionGrantState(
                admin,
                packageName,
                Manifest.permission.RECORD_AUDIO,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
            )
        }.onFailure { Log.e(TAG, "Cannot apply device policies", it) }
    }

    fun enterIfNeeded(activity: Activity) {
        if (_maintenance.value || isLocked) return
        runCatching { activity.startLockTask() }.onFailure { Log.w(TAG, "startLockTask failed", it) }
    }

    fun startMaintenance(activity: Activity) {
        _maintenance.value = true
        runCatching { activity.stopLockTask() }
        if (isDeviceOwner) {
            runCatching { dpm.setStatusBarDisabled(admin, false) }
        }
    }

    fun endMaintenance(activity: Activity) {
        if (!_maintenance.value) return
        _maintenance.value = false
        applyPolicies()
        enterIfNeeded(activity)
    }

    /** Gives the phone back its normal behaviour; the app can then be uninstalled. */
    @Suppress("DEPRECATION")
    fun removeDeviceOwner(activity: Activity) {
        _maintenance.value = true
        runCatching { activity.stopLockTask() }
        if (!isDeviceOwner) return
        runCatching {
            dpm.clearPackagePersistentPreferredActivities(admin, packageName)
            setHomeAliasEnabled(false)
            dpm.setStatusBarDisabled(admin, false)
            dpm.setKeyguardDisabled(admin, false)
            dpm.setLockTaskPackages(admin, emptyArray())
            dpm.clearDeviceOwnerApp(packageName)
        }.onFailure { Log.e(TAG, "Cannot remove device owner", it) }
    }

    private fun homeFilter() = IntentFilter(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addCategory(Intent.CATEGORY_DEFAULT)
    }

    private fun setHomeAliasEnabled(enabled: Boolean) {
        appContext.packageManager.setComponentEnabledSetting(
            homeAlias,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private companion object {
        const val TAG = "KioskManager"
    }
}
