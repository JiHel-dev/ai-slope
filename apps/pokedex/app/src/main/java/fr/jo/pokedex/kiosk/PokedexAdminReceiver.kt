package fr.jo.pokedex.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class PokedexAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        KioskManager(context).applyPolicies()
    }
}
