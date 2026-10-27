package com.example.isolation

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Official Android DeviceAdminReceiver used for provisioning and managing an Android
 * Managed Work Profile (Tier C hardware/OS-backed app isolation).
 */
class ClonexaDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        val adminComponent = getComponentName(context)
        try {
            dpm.setProfileName(adminComponent, "ClonexaPro Isolated Workspace")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dpm.setProfileEnabled(adminComponent)
            }
        } catch (_: SecurityException) {
            // Ignored if device policy restricts profile activation in current state
        }
    }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, ClonexaDeviceAdminReceiver::class.java)
        }
    }
}
