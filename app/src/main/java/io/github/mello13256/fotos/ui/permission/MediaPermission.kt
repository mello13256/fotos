package io.github.mello13256.fotos.ui.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class MediaAccess {
    /** Acesso a toda a biblioteca. */
    FULL,

    /** Android 14+: o utilizador escolheu apenas algumas fotografias. */
    PARTIAL,

    NONE,
}

object MediaPermission {

    /** Permissões a pedir. No Android 14+ incluir a de seleção parcial ativa o seletor do sistema. */
    val requested: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun access(context: Context): MediaAccess {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val images = granted(Manifest.permission.READ_MEDIA_IMAGES)
            val videos = granted(Manifest.permission.READ_MEDIA_VIDEO)
            val selected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            when {
                images && videos -> MediaAccess.FULL
                images || videos || selected -> MediaAccess.PARTIAL
                else -> MediaAccess.NONE
            }
        } else {
            if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) MediaAccess.FULL else MediaAccess.NONE
        }
    }
}
