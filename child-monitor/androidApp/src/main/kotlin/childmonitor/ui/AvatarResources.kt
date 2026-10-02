package childmonitor.ui

import childmonitor.android.R
import childmonitor.model.AvatarCatalog

object AvatarResources {
    fun drawable(id: String) = when (AvatarCatalog.resolve(id)) {
        "rabbit" -> R.drawable.avatar_rabbit
        "cat" -> R.drawable.avatar_cat
        else -> R.drawable.avatar_bear
    }
}
