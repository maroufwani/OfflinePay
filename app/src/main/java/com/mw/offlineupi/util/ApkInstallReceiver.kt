package com.mw.offlineupi.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast

/**
 * Receives the outcome of an [ApkInstaller] session.
 *
 * [PackageInstaller.STATUS_PENDING_USER_ACTION] is the normal first result on a sideloaded update:
 * the system hands back a confirmation Intent that must be started for the user to approve the
 * install. Without handling it, the session sat pending and the update silently never happened.
 */
class ApkInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ApkInstallReceiver"
        const val ACTION_INSTALL_STATUS = "com.mw.offlineupi.INSTALL_STATUS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm == null) {
                    Log.w(TAG, "Pending user action with no confirmation intent")
                    return
                }
                // The receiver has no activity of its own to launch from.
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(confirm)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not show install confirmation", e)
                }
            }

            PackageInstaller.STATUS_SUCCESS ->
                // The process is about to be replaced, so nothing more to do here.
                Log.d(TAG, "Update installed")

            else -> {
                Log.w(TAG, "Install failed (status=$status): $message")
                Toast.makeText(
                    context,
                    "Update could not be installed. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
