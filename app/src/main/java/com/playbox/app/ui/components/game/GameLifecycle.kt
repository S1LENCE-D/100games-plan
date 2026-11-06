package com.playbox.app.ui.components.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.playbox.app.ui.util.findActivity

/**
 * 只有应用真的进入后台时才暂停游戏；屏幕旋转引发的 Activity 重建由 ViewModel 承载，
 * 不应让玩家回来时看到暂停浮层。
 */
@Composable
fun GameBackgroundPauseEffect(onBackgroundPause: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (activity?.isChangingConfigurations != true) {
            onBackgroundPause()
        }
    }
}
