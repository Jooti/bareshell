package net.jooti.bareshell

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast

internal fun Context.isDefaultHome(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roles = getSystemService(RoleManager::class.java)
        if (roles?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
            return roles.isRoleHeld(RoleManager.ROLE_HOME)
        }
    }
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
}

@Suppress("DEPRECATION")
internal fun Activity.requestDefaultHome(requestCode: Int): Boolean {
    if (isDefaultHome()) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roles = getSystemService(RoleManager::class.java)
        if (roles?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
            try {
                startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), requestCode)
                return true
            } catch (error: ActivityNotFoundException) {
                Log.w("BareShellHome", "Home role chooser unavailable; opening Home settings", error)
            } catch (error: SecurityException) {
                Log.w("BareShellHome", "Home role request denied; opening Home settings", error)
            }
        }
    }
    return try {
        startActivityForResult(Intent(Settings.ACTION_HOME_SETTINGS), requestCode)
        true
    } catch (error: ActivityNotFoundException) {
        Log.e("BareShellHome", "Home settings unavailable", error)
        Toast.makeText(this, R.string.default_home_unavailable, Toast.LENGTH_LONG).show()
        false
    } catch (error: SecurityException) {
        Log.e("BareShellHome", "Home settings access denied", error)
        Toast.makeText(this, R.string.default_home_unavailable, Toast.LENGTH_LONG).show()
        false
    }
}
