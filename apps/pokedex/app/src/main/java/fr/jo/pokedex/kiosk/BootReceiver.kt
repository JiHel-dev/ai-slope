package fr.jo.pokedex.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.jo.pokedex.ui.MainActivity

/**
 * Fallback auto-start when the app is not device owner (Android 10+ may still refuse to start
 * an activity from the background). In managed mode the app is the home screen and starting it
 * here too would stack a second instance.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (KioskManager(context).isDeviceOwner) return
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { Log.w("BootReceiver", "Cannot start the app after ${intent.action}", it) }
    }
}
