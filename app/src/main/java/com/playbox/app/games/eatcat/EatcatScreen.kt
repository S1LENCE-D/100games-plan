package com.playbox.app.games.eatcat

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.AppContainer
import org.json.JSONTokener

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EatcatRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: EatcatViewModel = viewModel(
        factory = EatcatViewModel.factory(container.gameStatsRepository),
    )
    val webState by viewModel.webState.collectAsStateWithLifecycle()
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(AndroidColor.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = false
                mediaPlaybackRequiresUserGesture = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                ): Boolean {
                    val url = request?.url?.toString().orEmpty()
                    return !url.startsWith("file:///android_asset/eatcat/")
                }
            }
            loadUrl("file:///android_asset/eatcat/index.html")
        }
    }

    DisposableEffect(webView, viewModel) {
        val handler = Handler(Looper.getMainLooper())
        val poll = object : Runnable {
            override fun run() {
                webView.evaluateJavascript(EATCAT_STATE_SCRIPT) { raw ->
                    parseEvaluatedState(raw)?.let(viewModel::onWebState)
                }
                handler.postDelayed(this, 300)
            }
        }
        handler.postDelayed(poll, 700)
        onDispose { handler.removeCallbacksAndMessages(null) }
    }

    DisposableEffect(lifecycle, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = "吃掉小猫猫 · 网页版") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回游戏大厅",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { webView.reload() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "重新加载网页",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            EatcatHud(state = webState, bestScore = bestScore)
            AndroidView(
                factory = { webView },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun EatcatHud(
    state: EatcatWebState,
    bestScore: Int,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HudMetric(label = "网页得分", value = state.score.toString())
                HudMetric(
                    label = if (state.over) "平均 CPS" else "实时 CPS",
                    value = "%.2f".format(state.displayCps),
                )
                HudMetric(label = "按键命中", value = state.keyHits.toString())
                HudMetric(label = "行命中", value = state.rowHits.toString())
                HudMetric(label = "失误", value = state.errors.toString())
                HudMetric(label = "本机最佳", value = bestScore.toString())
                HudMetric(
                    label = "模式",
                    value = "${state.keyCount}K · ${state.pattern.take(12).ifBlank { "等待网页" }}",
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    state.over -> "网页已结算，本机最高分同步更新。"
                    state.started -> "数据由网页 Game 状态实时上报；网页内可暂停、重开和调整设置。"
                    else -> "网页已加载，开始游戏后这里会实时显示分数、命中和 CPS。"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HudMetric(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun parseEvaluatedState(raw: String?): EatcatWebState? {
    if (raw.isNullOrBlank() || raw == "null") return null
    return runCatching {
        val decoded = JSONTokener(raw).nextValue()
        if (decoded is String && decoded.isNotBlank()) EatcatWebState.fromJson(decoded) else null
    }.getOrNull()
}

private val EATCAT_STATE_SCRIPT: String by lazy {
    """
    (function(){
      try {
        var g = (typeof Game !== 'undefined') ? Game : window.Game;
        if (!g) return '';
        var cpsEl = document.querySelector('#cpsDisplay .cps-number');
        var hitsEl = document.querySelector('#cpsDisplay .cps-hits');
        var cpsText = cpsEl ? String(cpsEl.textContent || '') : '';
        var hitsText = hitsEl ? String(hitsEl.textContent || '') : '';
        var liveCps = parseFloat(cpsText);
        if (!isFinite(liveCps)) liveCps = 0;
        var now = Date.now();
        var start = g.date1 ? g.date1.getTime() : 0;
        var end = g.date2 ? g.date2.getTime() : now;
        var paused = Number(g._pausedDuration) || 0;
        if (g._pauseStart !== null && g._pauseStart !== undefined) paused += now - Number(g._pauseStart);
        var elapsedMs = start > 0 ? Math.max(0, end - start - paused) : 0;
        if (g.isPractice && Number(g.gameTimeNum) > 0) elapsedMs = Number(g.gameTimeNum) * 1000;
        var deviationMs = Number(g.deviationTime) || 0;
        var score = Number(g.gameScore) || 0;
        return JSON.stringify({
          score: score,
          keyHits: Number(g.gameKeyHits) || 0,
          rowHits: Number(g.gameRowHits) || 0,
          errors: Number(g.gameErrors) || 0,
          started: !!g.gameStart,
          over: !!g.gameOver,
          paused: !!g.gamePaused,
          keyCount: Number(g.keyCount) || 4,
          pattern: String(g.notePattern || '').slice(0, 60),
          timeLimit: Number(g.timeLimit) || 0,
          remaining: Number(g.gameTimeNum) || 0,
          isPractice: !!g.isPractice,
          elapsedMs: Math.round(elapsedMs),
          deviationMs: Math.round(deviationMs),
          liveCps: Math.round(liveCps * 100) / 100,
          averageCps: deviationMs > 0 ? Math.round(score * 100000 / deviationMs) / 100 : 0,
          cpsText: cpsText,
          hitsText: hitsText,
          scoreLayerVisible: !!(document.getElementById('GameScoreLayer') && document.getElementById('GameScoreLayer').style.display === 'block')
        });
      } catch (e) { return ''; }
    })()
    """.trimIndent()
}
