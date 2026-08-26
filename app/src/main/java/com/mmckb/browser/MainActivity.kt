package com.mmckb.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.URLUtil
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    companion object {
        private const val BING_SEARCH = "https://www.bing.com/search?q="
        private const val MAX_TAB_TITLE_LENGTH = 16
        private const val PREFERENCES_NAME = "browser_preferences"
        private const val KEY_JAVASCRIPT_ENABLED = "javascript_enabled"
        private const val KEY_DESKTOP_MODE = "desktop_mode"
        private const val KEY_BOOKMARKS = "bookmarks"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_TAB_LAYOUT_MODE = "tab_layout_mode"
        private const val KEY_FULL_TAB_CORNER_PERCENT = "full_tab_corner_percent"
        private const val LEGACY_STORAGE_PERMISSION_REQUEST_CODE = 4012
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 4013
        private const val KEY_DOWNLOAD_NOTIFICATIONS = "download_notifications_enabled"
        private const val KEY_SEARCH_ENGINE = "search_engine"
        private const val KEY_CUSTOM_SEARCH_URL = "custom_search_url"
        private const val KEY_PREDICTIVE_BACK = "predictive_back_gesture" // 仅用于迁移旧的布尔偏好。
        private const val KEY_BACK_ANIMATION_MODE = "back_animation_mode"
        private const val GITHUB_REPO_URL = "https://github.com/MMCKB/Browser"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }

    private enum class ThemeMode(val key: String, val label: String) {
        SYSTEM("system", "跟随系统"),
        LIGHT("light", "浅色"),
        DARK("dark", "深色");

        companion object {
            fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
        }
    }

    private enum class BackAnimationMode(val key: String, val label: String) {
        NONE("none", "无"),
        AOSP("aosp", "AOSP"),
        MIUIX("miuix", "Miuix"),
        SCALE("scale", "缩放"),
        CLASSIC("classic", "经典");

        companion object {
            fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: AOSP
        }
    }

    private enum class TabLayoutMode(val key: String, val label: String) {
        MINIMAL("minimal", "始终极简"),
        FULL("full", "始终完整");

        companion object {
            fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: MINIMAL
        }
    }

    private data class Palette(
        val page: Int,
        val card: Int,
        val cardStroke: Int,
        val group: Int,
        val input: Int,
        val chip: Int,
        val selectedChip: Int,
        val text: Int,
        val mutedText: Int,
        val icon: Int,
        val divider: Int,
        val accent: Int,
        val homeBadge: Int,
        val actionBackground: Int
    )

    private data class BrowserTab(
        val id: Long,
        val webView: WebView,
        val defaultUserAgent: String,
        var title: String = "新标签页",
        var url: String = "",
        var isHome: Boolean = true
    )

    private data class PendingDownload(
        val url: String,
        val userAgent: String,
        val contentDisposition: String?,
        val mimeType: String?
    )

    private data class DownloadSpeedSample(val bytes: Long, val atMillis: Long)

    data class SearchEngine(val key: String, val name: String, val urlTemplate: String)

    val searchEnginePresets = listOf(
        SearchEngine("bing", "Bing", "https://www.bing.com/search?q="),
        SearchEngine("google", "Google", "https://www.google.com/search?q="),
        SearchEngine("baidu", "百度", "https://www.baidu.com/s?wd="),
        SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
        SearchEngine("custom", "自定义", "")
    )

    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabId: Long = -1L
    private var nextTabId: Long = 1L
    private var javascriptEnabled = true
    private var desktopModeEnabled = false
    private var themeMode = ThemeMode.SYSTEM
    private var tabLayoutMode = TabLayoutMode.MINIMAL
    private var fullTabCornerPercent = 28
    private var downloadNotificationsEnabled = false
    private var searchEngineKey = "bing"
    private var customSearchUrl = ""
    private var backAnimationMode = BackAnimationMode.AOSP

    private lateinit var preferences: SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var webContainer: FrameLayout
    private lateinit var systemBarsScrim: FrameLayout
    private lateinit var topSystemBarScrim: View
    private lateinit var bottomSystemBarScrim: View
    private lateinit var bottomControlCard: FrameLayout
    private lateinit var frostedPill: FrameLayout
    private lateinit var frostedWebBackdrop: ImageView
    private val frostedBackdropHandler = Handler(Looper.getMainLooper())
    private val refreshFrostedBackdropRunnable = Runnable { updateFrostedWebBackdrop() }
    private lateinit var tabCountButton: MaterialButton
    private lateinit var tabStrip: LinearLayout
    private lateinit var addressField: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var settingsOverlay: FrameLayout
    private lateinit var settingsDialog: MaterialCardView
    private lateinit var tabToolsOverlay: FrameLayout
    private lateinit var tabChooserOverlay: FrameLayout
    private lateinit var tabChooserCard: FrameLayout
    private lateinit var tabChooserContent: LinearLayout
    private lateinit var tabChooserWebBackdrop: ImageView
    private lateinit var downloadsOverlay: FrameLayout
    private lateinit var downloadConfirmOverlay: FrameLayout
    private lateinit var downloadConfirmDialog: MaterialCardView
    private lateinit var appInfoOverlay: FrameLayout
    private lateinit var appInfoCard: MaterialCardView
    private lateinit var browserBackCallback: OnBackPressedCallback
    private var pendingLegacyDownload: PendingDownload? = null
    private val pendingNotificationDownloadIds = linkedSetOf<Long>()
    private var isSettingsClosing = false
    private var settingsStartTranslationX = 0f
    private var settingsStartTranslationY = 0f
    private var settingsAnchorPoint: PointF? = null
    private var tabToolsAnchorPoint: PointF? = null
    private var tabToolsStartTranslationX = 0f
    private var tabToolsStartTranslationY = 0f
    private var tabChooserAnchorPoint: PointF? = null
    private var tabChooserStartTranslationX = 0f
    private var tabChooserStartTranslationY = 0f
    private var systemBarInsets: Insets = Insets.NONE
    private var downloadSelectionMode = false
    private val selectedDownloadIds = linkedSetOf<Long>()
    private val downloadSpeedSamples = mutableMapOf<Long, DownloadSpeedSample>()
    private var downloadsRefreshAction: (() -> Unit)? = null
    private val downloadsRefreshHandler = Handler(Looper.getMainLooper())
    private val downloadsRefreshRunnable = object : Runnable {
        override fun run() {
            downloadsRefreshAction?.invoke()
            if (::downloadsOverlay.isInitialized && downloadsOverlay.parent != null) {
                downloadsRefreshHandler.postDelayed(this, 1_000L)
            }
        }
    }
    private var backGestureTarget: View? = null

    private val activeTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == activeTabId }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        javascriptEnabled = preferences.getBoolean(KEY_JAVASCRIPT_ENABLED, true)
        desktopModeEnabled = preferences.getBoolean(KEY_DESKTOP_MODE, false)
        themeMode = ThemeMode.fromKey(preferences.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.key))
        tabLayoutMode = TabLayoutMode.fromKey(preferences.getString(KEY_TAB_LAYOUT_MODE, TabLayoutMode.MINIMAL.key))
        fullTabCornerPercent = preferences.getInt(KEY_FULL_TAB_CORNER_PERCENT, 28).coerceIn(0, 100)
        downloadNotificationsEnabled = preferences.getBoolean(KEY_DOWNLOAD_NOTIFICATIONS, false) && hasDownloadNotificationPermission()
        searchEngineKey = preferences.getString(KEY_SEARCH_ENGINE, "bing") ?: "bing"
        customSearchUrl = preferences.getString(KEY_CUSTOM_SEARCH_URL, "") ?: ""
        val storedBackMode = preferences.getString(KEY_BACK_ANIMATION_MODE, null)
        backAnimationMode = if (storedBackMode == null) {
            if (preferences.getBoolean(KEY_PREDICTIVE_BACK, true)) BackAnimationMode.AOSP else BackAnimationMode.NONE
        } else {
            BackAnimationMode.fromKey(storedBackMode)
        }
        preferences.edit().putString(KEY_BACK_ANIMATION_MODE, backAnimationMode.key).apply()
        // AndroidManifest 启用系统预测性返回；只有 AOSP 模式的根页面会主动交还系统动画。

        root = FrameLayout(this)
        // 保持系统状态栏/导航栏可见；仅让网页背景延伸到系统栏下方。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        installSystemBarInsets()
        webContainer = FrameLayout(this)
        root.addView(
            webContainer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                // 网页全高渲染，透明 Tab 区直接透出正在浏览的内容。
                setMargins(0, 0, 0, 0)
            }
        )
        installSystemBarsScrim()
        attachBottomControls()
        setContentView(root)
        // 仅在根视图已附着到窗口后控制透明系统栏；部分设备会在启动阶段对未附着 View 的 Insets 控制器抛异常。
        root.post { applySystemBarPresentation() }
        createTab()

        browserBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                beginBackGestureAnimation()
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                applyBackGestureProgress(backEvent.progress, backEvent.swipeEdge == BackEventCompat.EDGE_RIGHT)
            }

            override fun handleOnBackCancelled() {
                cancelBackGestureAnimation()
            }

            override fun handleOnBackPressed() {
                commitBackGestureAnimation()
            }
        }
        onBackPressedDispatcher.addCallback(this, browserBackCallback)
        updateBrowserBackCallback()
    }

    private fun currentPalette(): Palette = buildCurrentPalette(isDarkPalette())

    private fun buildCurrentPalette(dark: Boolean): Palette = if (dark) {
        Palette(
            page = Color.rgb(18, 18, 20),
            card = Color.argb(218, 36, 36, 39),
            cardStroke = Color.argb(100, 112, 112, 120),
            group = Color.rgb(54, 54, 59),
            input = Color.rgb(42, 42, 47),
            chip = Color.rgb(66, 66, 72),
            selectedChip = Color.rgb(78, 92, 108),
            text = Color.rgb(244, 244, 246),
            mutedText = Color.rgb(192, 192, 198),
            icon = Color.rgb(234, 234, 240),
            divider = Color.argb(100, 190, 190, 198),
            accent = Color.rgb(112, 199, 255),
            homeBadge = Color.rgb(51, 123, 177),
            actionBackground = Color.rgb(58, 58, 64)
        )
    } else {
        Palette(
            page = Color.rgb(247, 247, 250),
            card = Color.argb(235, 252, 252, 253),
            cardStroke = Color.argb(70, 200, 200, 204),
            group = Color.WHITE,
            input = Color.rgb(242, 242, 245),
            chip = Color.argb(235, 240, 240, 243),
            selectedChip = Color.argb(230, 220, 228, 238),
            text = Color.rgb(28, 28, 30),
            mutedText = Color.rgb(110, 110, 114),
            icon = Color.rgb(44, 44, 46),
            divider = Color.argb(60, 180, 180, 184),
            accent = Color.rgb(22, 119, 181),
            homeBadge = Color.rgb(33, 126, 190),
            actionBackground = Color.rgb(232, 232, 236)
        )
    }

    private fun installSystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            updateBottomControlPosition()
            updateSystemBarsScrim()
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun installSystemBarsScrim() {
        systemBarsScrim = FrameLayout(this).apply {
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        topSystemBarScrim = View(this)
        bottomSystemBarScrim = View(this)
        systemBarsScrim.addView(topSystemBarScrim)
        systemBarsScrim.addView(bottomSystemBarScrim)
        root.addView(
            systemBarsScrim,
            1,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        updateSystemBarsScrim()
    }

    private fun updateSystemBarsScrim() {
        if (!::systemBarsScrim.isInitialized || systemBarsScrim.parent == null) return
        val dark = isDarkPalette()
        val topTint = if (dark) Color.argb(126, 18, 18, 20) else Color.argb(98, 255, 255, 255)
        val bottomTint = if (dark) Color.argb(148, 18, 18, 20) else Color.argb(112, 255, 255, 255)
        topSystemBarScrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topTint, Color.TRANSPARENT)
        )
        bottomSystemBarScrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, bottomTint)
        )
        topSystemBarScrim.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (systemBarInsets.top + dp(22)).coerceAtLeast(dp(22)),
            Gravity.TOP
        )
        bottomSystemBarScrim.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (systemBarInsets.bottom + dp(26)).coerceAtLeast(dp(26)),
            Gravity.BOTTOM
        )
    }

    private fun applySystemBarPresentation() {
        // 保持状态栏和导航栏由系统绘制，网页在其下延伸；渐变遮罩只用于柔化图标与网页内容的交界。
        // 关键防护：不要在 setContentView() 前构造 Insets 控制器，避免部分设备启动时因 View 尚未附着而闪退。
        if (!root.isAttachedToWindow) return
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = !isDarkPalette()
            isAppearanceLightNavigationBars = !isDarkPalette()
        }
        updateSystemBarsScrim()
    }

    private fun bottomTabOuterGap(): Int = dp(10)

    private fun fullTabCornerRadius(): Int =
        (dp(40) * fullTabCornerPercent.coerceIn(0, 100) / 100f).roundToInt()

    private fun updateBottomControlPosition() {
        if (!::bottomControlCard.isInitialized || bottomControlCard.parent == null) return
        val gap = bottomTabOuterGap()
        (bottomControlCard.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            // 基础留白四边均为 10dp；底部额外叠加系统导航栏 inset，避免遮挡系统任务栏。
            params.setMargins(
                gap + systemBarInsets.left,
                gap,
                gap + systemBarInsets.right,
                gap + systemBarInsets.bottom
            )
            params.gravity = Gravity.BOTTOM
            bottomControlCard.layoutParams = params
        }
    }

    private fun attachBottomControls() {
        if (::bottomControlCard.isInitialized) {
            root.removeView(bottomControlCard)
        }
        val palette = currentPalette()
        root.setBackgroundColor(palette.page)
        bottomControlCard = buildBottomControlCard()
        // 极简模式为短椭圆胶囊；完整模式是装下双行控件的圆角长方形容器。
        val tabBarHeight = dp(if (tabLayoutMode == TabLayoutMode.FULL) 96 else 54)
        root.addView(
            bottomControlCard,
            1,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                tabBarHeight,
                Gravity.BOTTOM
            )
        )
        updateBottomControlPosition()
        if (root.isAttachedToWindow) applySystemBarPresentation()
        bottomControlCard.post { scheduleFrostedBackdropRefresh() }
    }

    private fun buildBottomControlCard(): FrameLayout {
        // 外层完全透明，仅负责锚定在屏幕底部；网页会在胶囊四周完整透出。
        return FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            val palette = currentPalette()
            val visualGap = dp(6)
            frostedPill = FrameLayout(this@MainActivity).apply {
                // 极简模式保持椭圆磨砂胶囊；完整模式使用四角圆润的长方形容器。
                val radius = if (tabLayoutMode == TabLayoutMode.FULL) fullTabCornerRadius() else dp(21)
                background = roundedBackground(Color.TRANSPARENT, radius, palette.cardStroke)
                clipToOutline = true
                frostedWebBackdrop = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.MATRIX
                    alpha = if (isDarkPalette()) 0.46f else 0.62f
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRenderEffect(RenderEffect.createBlurEffect(dp(18).toFloat(), dp(18).toFloat(), Shader.TileMode.CLAMP))
                    }
                }
                addView(frostedWebBackdrop, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
                addView(View(this@MainActivity).apply {
                    // 低透明度着色层只覆盖胶囊，不能形成整块底栏。
                    setBackgroundColor(if (isDarkPalette()) Color.argb(76, 18, 20, 26) else Color.argb(86, 255, 255, 255))
                }, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    // 所有控件到胶囊边缘的上下左右距离均为 5dp。
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                    addView(buildPrimaryControlRow())
                    if (tabLayoutMode == TabLayoutMode.FULL) {
                        addView(buildFullTabRow(), LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(34)
                        ).apply { topMargin = dp(4) })
                    }
                })
            }
            addView(frostedPill, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                // 胶囊与透明定位容器的四边间距一致。
                setMargins(visualGap, visualGap, visualGap, visualGap)
            })
        }
    }

    private fun frostedSurfaceColor(): Int =
        if (isDarkPalette()) Color.argb(118, 35, 39, 49) else Color.argb(176, 255, 255, 255)

    private fun frostedInputColor(): Int =
        if (isDarkPalette()) Color.argb(152, 30, 34, 43) else Color.argb(194, 255, 255, 255)

    private fun scheduleFrostedBackdropRefresh() {
        if (!::bottomControlCard.isInitialized) return
        frostedBackdropHandler.removeCallbacks(refreshFrostedBackdropRunnable)
        frostedBackdropHandler.postDelayed(refreshFrostedBackdropRunnable, 120L)
    }

    private fun updateFrostedWebBackdrop() {
        if (!::bottomControlCard.isInitialized || !::frostedPill.isInitialized || !::frostedWebBackdrop.isInitialized ||
            webContainer.width <= 0 || webContainer.height <= 0 || frostedPill.width <= 0 || frostedPill.height <= 0
        ) return
        val rootLocation = IntArray(2)
        val cardLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        frostedPill.getLocationOnScreen(cardLocation)
        val cardTopInRoot = (cardLocation[1] - rootLocation[1]).coerceAtLeast(0)
        val sourceY = cardTopInRoot.coerceAtMost((webContainer.height - 1).coerceAtLeast(0))
        val sourceHeight = (webContainer.height - sourceY).coerceAtLeast(1)
        val source = try {
            Bitmap.createBitmap(webContainer.width, sourceHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.translate(0f, -sourceY.toFloat())
                webContainer.draw(canvas)
            }
        } catch (_: OutOfMemoryError) {
            return
        }
        val scaled = Bitmap.createScaledBitmap(source, frostedPill.width, frostedPill.height, true)
        if (scaled !== source) source.recycle()
        frostedWebBackdrop.setImageBitmap(scaled)
    }

    private fun buildPrimaryControlRow(): LinearLayout {
        val palette = currentPalette()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(buildOvalNavigationGroup())
            addressField = EditText(this@MainActivity).apply {
                setSingleLine(true)
                textSize = 14f
                // 材质：高对比度文本，保证毛玻璃上可读性
                setTextColor(palette.text)
                setHintTextColor(palette.mutedText)
                hint = "搜索或网址"
                setPadding(dp(10), 0, dp(10), 0)
                // 在磨砂网页背景上使用半透明输入面，保留网页层次并维持可读性。
                background = roundedBackground(frostedInputColor(), dp(16), palette.cardStroke)
                imeOptions = EditorInfo.IME_ACTION_GO
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
                setOnEditorActionListener { _, actionId, event ->
                    if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                        loadAddressInput()
                        true
                    } else false
                }
            }
            addView(addressField, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                marginStart = dp(5)
                marginEnd = dp(4)
            })
            if (tabLayoutMode == TabLayoutMode.MINIMAL) {
                tabCountButton = compactIconButton("▣ 0", "切换标签页") { anchor -> showTabChooser(anchor) }.apply {
                    textSize = 12f
                    contentDescription = "切换标签页"
                }
                addView(tabCountButton)
            } else {
                addView(compactIconButton("⌂", "返回独立首页") { showHome(activeTab ?: return@compactIconButton) })
            }
            // 新建标签页统一收纳到极简模式的标签页浮层底部，避免主 Tab 栏出现独立加号。
            addView(compactIconButton("⋮", "打开工具栏") { anchor -> showTabTools(anchor) })
            addView(ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).also { bar ->
                progressBar = bar
                bar.max = 100
                bar.progressTintList = ColorStateList.valueOf(palette.accent)
                bar.visibility = View.GONE
            }, LinearLayout.LayoutParams(dp(1), dp(1)))
        }
    }

    private fun buildOvalNavigationGroup(): MaterialCardView {
        val palette = currentPalette()
        return MaterialCardView(this).apply {
            radius = dp(17).toFloat()
            cardElevation = 0f
            // 半透明交互底座，背景网页仍可通过外围磨砂层感知。
            setCardBackgroundColor(frostedSurfaceColor())
            strokeColor = palette.cardStroke
            strokeWidth = dp(1)
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(1), 0, dp(1), 0)
                addView(ovalNavButton("‹", "后退") { goBack() })
                addView(ovalNavButton("›", "前进") { goForward() })
                addView(ovalNavButton("↻", "刷新页面") { activeTab?.webView?.reload() })
            })
        }
    }

    private fun buildFullTabRow(): LinearLayout {
        val palette = currentPalette()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val tabsScroller = android.widget.HorizontalScrollView(this@MainActivity).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(LinearLayout(this@MainActivity).also { tabStrip = it }.apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                })
            }
            addView(tabsScroller, LinearLayout.LayoutParams(0, dp(32), 1f))
            addView(TextView(this@MainActivity).apply {
                text = "＋"
                textSize = 18f
                gravity = Gravity.CENTER
                contentDescription = "新建标签页"
                setTextColor(palette.icon)
                background = roundedBackground(frostedSurfaceColor(), dp(13), palette.cardStroke)
                setOnClickListener { createTab() }
            }, LinearLayout.LayoutParams(dp(28), dp(32)))
        }
    }

    private fun showTabChooser(anchor: View) {
        if (::tabChooserOverlay.isInitialized && tabChooserOverlay.parent != null) {
            hideTabChooser()
            return
        }
        val palette = currentPalette()
        tabChooserAnchorPoint = captureAnchor(anchor)
        tabChooserOverlay = FrameLayout(this).apply {
            // 仅作为透明点击承载层，不绘制全屏遮罩；视觉上只出现独立小浮窗。
            isClickable = true
            alpha = 1f
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { hideTabChooser() }
        }
        tabChooserCard = FrameLayout(this).apply {
            background = roundedBackground(Color.TRANSPARENT, dp(21), palette.cardStroke)
            clipToOutline = true
            elevation = dp(16).toFloat()
            translationZ = dp(8).toFloat()
            alpha = 0f
            scaleX = 0.16f
            scaleY = 0.16f
            setOnClickListener { /* 点击卡片本身不关闭。 */ }

            tabChooserWebBackdrop = ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.MATRIX
                alpha = if (isDarkPalette()) 0.46f else 0.62f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRenderEffect(RenderEffect.createBlurEffect(dp(18).toFloat(), dp(18).toFloat(), Shader.TileMode.CLAMP))
                }
            }
            addView(tabChooserWebBackdrop, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(View(this@MainActivity).apply {
                setBackgroundColor(if (isDarkPalette()) Color.argb(76, 18, 20, 26) else Color.argb(86, 255, 255, 255))
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            tabChooserContent = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
            }
            addView(tabChooserContent, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        refreshTabChooserContent()
        tabChooserOverlay.addView(
            tabChooserCard,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                // 与工具栏相同宽度与位置，但保持独立展示，不与底部 Tab 相连。
                setMargins(dp(8), 0, dp(8), dp(62))
            }
        )
        root.addView(tabChooserOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        updateBrowserBackCallback()
        tabChooserCard.post {
            updateTabChooserWebBackdrop()
            val cardLocation = IntArray(2)
            tabChooserCard.getLocationOnScreen(cardLocation)
            val anchorPoint = tabChooserAnchorPoint
                ?: PointF(cardLocation[0] + tabChooserCard.width / 2f, cardLocation[1] + tabChooserCard.height.toFloat())
            tabChooserCard.pivotX = (anchorPoint.x - cardLocation[0]).coerceIn(0f, tabChooserCard.width.toFloat())
            tabChooserCard.pivotY = (anchorPoint.y - cardLocation[1]).coerceIn(0f, tabChooserCard.height.toFloat())
            tabChooserStartTranslationX = anchorPoint.x - (cardLocation[0] + tabChooserCard.pivotX)
            tabChooserStartTranslationY = anchorPoint.y - (cardLocation[1] + tabChooserCard.pivotY)
            tabChooserCard.translationX = tabChooserStartTranslationX
            tabChooserCard.translationY = tabChooserStartTranslationY
            tabChooserCard.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .translationX(0f).translationY(0f)
                .setDuration(220)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.04f))
                .start()
        }
    }

    private fun refreshTabChooserContent() {
        if (!::tabChooserContent.isInitialized) return
        val palette = currentPalette()
        tabChooserContent.removeAllViews()
        tabChooserContent.addView(TextView(this).apply {
            text = "标签页 · ${tabs.size}"
            textSize = 13f
            setTextColor(palette.mutedText)
            setPadding(dp(4), 0, dp(4), dp(7))
        })

        // 两列横向标签网格：标签少时保持很小的窗口；增加标签时按行向上叠放。
        val tabRows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        tabs.chunked(2).forEachIndexed { rowIndex, rowTabs ->
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            rowTabs.forEachIndexed { columnIndex, tab ->
                val selected = tab.id == activeTabId
                val item = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(9), 0, dp(2), 0)
                    background = roundedBackground(
                        if (selected) frostedSurfaceColor() else Color.argb(if (isDarkPalette()) 58 else 72, 255, 255, 255),
                        dp(14),
                        if (selected) palette.cardStroke else Color.argb(40, 180, 180, 184)
                    )
                    contentDescription = "${trimTabTitle(if (tab.isHome) "主页" else tab.title)} 标签页"
                    setOnClickListener {
                        selectTab(tab.id)
                        hideTabChooser()
                    }
                }
                item.addView(TextView(this@MainActivity).apply {
                    text = trimTabTitle(if (tab.isHome) "主页" else tab.title)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    textSize = 13f
                    setTextColor(if (selected) palette.text else palette.mutedText)
                }, LinearLayout.LayoutParams(0, dp(42), 1f))
                item.addView(MaterialButton(this@MainActivity).apply {
                    text = "×"
                    textSize = 17f
                    contentDescription = "关闭 ${trimTabTitle(if (tab.isHome) "主页" else tab.title)} 标签页"
                    minWidth = 0
                    minHeight = 0
                    insetTop = 0
                    insetBottom = 0
                    setPadding(0, 0, 0, 0)
                    cornerRadius = dp(12)
                    backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    setTextColor(palette.icon)
                    setOnClickListener {
                        closeTab(tab.id)
                        refreshTabChooserContent()
                    }
                }, LinearLayout.LayoutParams(dp(32), dp(42)))
                row.addView(item, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    if (columnIndex == 0 && rowTabs.size > 1) marginEnd = dp(6)
                })
            }
            if (rowTabs.size == 1) {
                row.addView(View(this@MainActivity), LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(6) })
            }
            tabRows.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
            ).apply { if (rowIndex > 0) topMargin = dp(6) })
        }
        val maxRowsHeight = minOf(dp(186), (root.height * 0.36f).roundToInt().coerceAtLeast(dp(90)))
        tabChooserContent.addView(android.widget.ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(tabRows)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            minOf(dp(42) * ((tabs.size + 1) / 2) + dp(6) * ((tabs.size - 1) / 2), maxRowsHeight)
        ))
        tabChooserContent.addView(MaterialButton(this).apply {
            text = "＋  新建标签页"
            textSize = 14f
            isAllCaps = false
            contentDescription = "新建标签页"
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(15)
            backgroundTintList = ColorStateList.valueOf(frostedSurfaceColor())
            setTextColor(palette.icon)
            setOnClickListener {
                hideTabChooser { createTab() }
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(42)
        ).apply { topMargin = dp(8) })
        tabChooserContent.post { updateTabChooserWebBackdrop() }
    }

    private fun updateTabChooserWebBackdrop() {
        if (!::tabChooserCard.isInitialized || !::tabChooserWebBackdrop.isInitialized ||
            webContainer.width <= 0 || webContainer.height <= 0 ||
            tabChooserCard.width <= 0 || tabChooserCard.height <= 0
        ) return
        val rootLocation = IntArray(2)
        val cardLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        tabChooserCard.getLocationOnScreen(cardLocation)
        val cardTopInRoot = (cardLocation[1] - rootLocation[1]).coerceAtLeast(0)
        val sourceY = cardTopInRoot.coerceAtMost((webContainer.height - 1).coerceAtLeast(0))
        val sourceHeight = (webContainer.height - sourceY).coerceAtLeast(1)
        val source = try {
            Bitmap.createBitmap(webContainer.width, sourceHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.translate(0f, -sourceY.toFloat())
                webContainer.draw(canvas)
            }
        } catch (_: OutOfMemoryError) {
            return
        }
        val scaled = Bitmap.createScaledBitmap(source, tabChooserCard.width, tabChooserCard.height, true)
        if (scaled !== source) source.recycle()
        tabChooserWebBackdrop.setImageBitmap(scaled)
    }

    private fun hideTabChooser(after: () -> Unit = {}) {
        if (!::tabChooserOverlay.isInitialized || tabChooserOverlay.parent == null) {
            after()
            return
        }
        tabChooserCard.animate().alpha(0f).scaleX(0.16f).scaleY(0.16f)
            .translationX(tabChooserStartTranslationX).translationY(tabChooserStartTranslationY)
            .setDuration(180)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .start()
        tabChooserCard.postDelayed({
            if (tabChooserOverlay.parent != null) root.removeView(tabChooserOverlay)
            tabChooserAnchorPoint = null
            tabChooserStartTranslationX = 0f
            tabChooserStartTranslationY = 0f
            updateBrowserBackCallback()
            after()
        }, 190L)
    }

    private fun ovalNavButton(symbol: String, description: String, action: () -> Unit): MaterialButton {
        val palette = currentPalette()
        return MaterialButton(this).apply {
            text = symbol
            textSize = 18f
            contentDescription = description
            minWidth = 0
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            setPadding(0, 0, 0, 0)
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(palette.icon)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(34))
            setOnClickListener { action() }
        }
    }

    private fun compactIconButton(symbol: String, description: String, onClick: (View) -> Unit): MaterialButton {
        val palette = currentPalette()
        return MaterialButton(this).apply {
            text = symbol
            textSize = 16f
            contentDescription = description
            minWidth = 0
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            setPadding(0, 0, 0, 0)
            cornerRadius = dp(13)
            // 半透明按钮表面，与底部网页磨砂背景保持一致。
            backgroundTintList = ColorStateList.valueOf(frostedSurfaceColor())
            setTextColor(palette.icon)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(34)).apply { marginEnd = dp(1) }
            setOnClickListener { onClick(it) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createTab() {
        val webView = WebView(this).apply { setBackgroundColor(Color.WHITE) }
        val tab = BrowserTab(
            id = nextTabId++,
            webView = webView,
            defaultUserAgent = webView.settings.userAgentString
        )
        applyWebSettings(tab)
        configureWebView(tab)
        tabs += tab
        selectTab(tab.id)
        updateBrowserBackCallback()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applyWebSettings(tab: BrowserTab) {
        tab.webView.settings.apply {
            javaScriptEnabled = javascriptEnabled
            domStorageEnabled = true
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = if (desktopModeEnabled) DESKTOP_USER_AGENT else tab.defaultUserAgent
        }
    }

    private fun configureWebView(tab: BrowserTab) {
        tab.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return if (uri.scheme == "http" || uri.scheme == "https") false else {
                    openExternalUri(uri)
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                tab.isHome = false
                tab.url = url
                if (tab.id == activeTabId) {
                    addressField.setText(url)
                    progressBar.visibility = View.VISIBLE
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                tab.url = url
                if (tab.id == activeTabId) {
                    addressField.setText(url)
                    progressBar.visibility = View.GONE
                    scheduleFrostedBackdropRefresh()
                    updateBrowserBackCallback()
                }
                refreshTabStrip()
            }
        }
        tab.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (tab.id == activeTabId) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                }
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                tab.title = title?.takeIf { it.isNotBlank() } ?: hostLabel(tab.url)
                refreshTabStrip()
            }
        }
        tab.webView.setOnScrollChangeListener { _, _, _, _, _ ->
            if (tab.id == activeTabId) scheduleFrostedBackdropRefresh()
        }
        tab.webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            enqueueWebDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun selectTab(tabId: Long) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        activeTabId = tabId
        renderActiveTab(tab)
        refreshTabStrip()
        updateBrowserBackCallback()
    }

    private fun renderActiveTab(tab: BrowserTab) {
        webContainer.removeAllViews()
        if (tab.isHome) {
            webContainer.addView(buildHomeScreen(tab))
            addressField.setText("")
        } else {
            webContainer.addView(
                tab.webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addressField.setText(tab.url)
        }
        addressField.clearFocus()
        progressBar.visibility = View.GONE
        scheduleFrostedBackdropRefresh()
    }

    private fun buildHomeScreen(tab: BrowserTab): View {
        val palette = currentPalette()
        return FrameLayout(this).apply {
            setBackgroundColor(palette.page)
            val content = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(28), dp(28), dp(28), dp(32))
            }
            content.addView(TextView(this@MainActivity).apply {
                text = searchEnginePresets.firstOrNull { it.key == searchEngineKey }?.name ?: "搜索"
                textSize = 38f
                setTextColor(palette.text)
                gravity = Gravity.CENTER
            })
            content.addView(TextView(this@MainActivity).apply {
                text = "浮悬浏览器独立搜索页"
                textSize = 14f
                setTextColor(palette.mutedText)
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, dp(22))
            })
            val searchField = EditText(this@MainActivity).apply {
                setSingleLine(true)
                textSize = 16f
                hint = "搜索或输入网址"
                setTextColor(palette.text)
                setHintTextColor(palette.mutedText)
                setPadding(dp(16), 0, dp(16), 0)
                background = roundedBackground(palette.input, dp(22), palette.cardStroke)
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                setOnEditorActionListener { _, actionId, event ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                        submitHomeSearch(tab, text.toString())
                        true
                    } else false
                }
            }
            content.addView(
                searchField,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
            )
            content.addView(MaterialButton(this@MainActivity).apply {
                text = "搜索"
                textSize = 15f
                isAllCaps = false
                setTextColor(if (isDarkPalette()) Color.WHITE else Color.rgb(255, 255, 255))
                backgroundTintList = ColorStateList.valueOf(palette.homeBadge)
                cornerRadius = dp(22)
                insetTop = 0
                insetBottom = 0
                setOnClickListener { submitHomeSearch(tab, searchField.text.toString()) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply {
                topMargin = dp(12)
            })
            addView(
                content,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }
    }

    private fun currentSearchUrlTemplate(): String {
        val preset = searchEnginePresets.firstOrNull { it.key == searchEngineKey }
        return if (searchEngineKey == "custom" && customSearchUrl.isNotBlank()) {
            customSearchUrl
        } else {
            preset?.urlTemplate ?: BING_SEARCH
        }
    }

    private fun submitHomeSearch(tab: BrowserTab, query: String) {
        val text = query.trim()
        if (text.isBlank()) return
        if (tab.id != activeTabId) selectTab(tab.id)
        navigateToUrl(currentSearchUrlTemplate() + Uri.encode(text))
    }

    private fun showHome(tab: BrowserTab) {
        tab.webView.stopLoading()
        tab.isHome = true
        tab.url = ""
        tab.title = "新标签页"
        if (tab.id == activeTabId) renderActiveTab(tab)
        refreshTabStrip()
        updateBrowserBackCallback()
    }

    private fun navigateToUrl(url: String) {
        val tab = activeTab ?: return
        tab.isHome = false
        tab.url = url
        if (webContainer.childCount == 0 || webContainer.getChildAt(0) != tab.webView) {
            renderActiveTab(tab)
        }
        tab.webView.loadUrl(url)
        updateBrowserBackCallback()
    }

    private fun loadAddressInput() {
        val input = addressField.text.toString().trim()
        when {
            input.isBlank() -> activeTab?.let(::showHome)
            input.startsWith("https://", ignoreCase = true) || input.startsWith("http://", ignoreCase = true) -> navigateToUrl(input)
            input.contains(".") && !input.contains(" ") -> navigateToUrl("https://$input")
            else -> navigateToUrl(currentSearchUrlTemplate() + Uri.encode(input))
        }
        hideKeyboard()
    }

    private fun hasOpenOverlay(): Boolean =
        (::appInfoOverlay.isInitialized && appInfoOverlay.parent != null) ||
            (::downloadConfirmOverlay.isInitialized && downloadConfirmOverlay.parent != null) ||
            (::downloadsOverlay.isInitialized && downloadsOverlay.parent != null) ||
            (::settingsOverlay.isInitialized && settingsOverlay.parent != null) ||
            (::tabToolsOverlay.isInitialized && tabToolsOverlay.parent != null) ||
            (::tabChooserOverlay.isInitialized && tabChooserOverlay.parent != null)

    private fun canHandleBrowserBack(): Boolean {
        val tab = activeTab ?: return false
        return !tab.isHome || tabs.size > 1
    }

    private fun updateBrowserBackCallback() {
        if (!::browserBackCallback.isInitialized) return
        val hasInternalDestination = hasOpenOverlay() || canHandleBrowserBack()
        // AOSP 模式在根页禁用回调并把手势交给系统，从而显示回到桌面的系统预览。
        browserBackCallback.isEnabled = hasInternalDestination || backAnimationMode != BackAnimationMode.AOSP
    }

    private fun resolveBackVisualTarget(): View? = when {
        ::appInfoOverlay.isInitialized && appInfoOverlay.parent != null -> appInfoCard
        ::downloadConfirmOverlay.isInitialized && downloadConfirmOverlay.parent != null -> downloadConfirmDialog
        ::downloadsOverlay.isInitialized && downloadsOverlay.parent != null -> downloadsOverlay.getChildAt(0)
        ::settingsOverlay.isInitialized && settingsOverlay.parent != null -> settingsDialog
        ::tabToolsOverlay.isInitialized && tabToolsOverlay.parent != null -> tabToolsOverlay.getChildAt(0)
        ::tabChooserOverlay.isInitialized && tabChooserOverlay.parent != null -> tabChooserCard
        else -> webContainer.takeIf { it.childCount > 0 }
    }

    private fun beginBackGestureAnimation() {
        backGestureTarget = resolveBackVisualTarget()
    }

    private fun applyBackGestureProgress(progress: Float, fromRight: Boolean) {
        val target = backGestureTarget ?: return
        val fraction = progress.coerceIn(0f, 1f)
        when (backAnimationMode) {
            BackAnimationMode.MIUIX -> {
                val direction = if (fromRight) -1f else 1f
                target.translationX = direction * target.width * 0.12f * fraction
                target.translationY = dp(8).toFloat() * fraction
                target.scaleX = 1f - 0.035f * fraction
                target.scaleY = 1f - 0.035f * fraction
                target.alpha = 1f - 0.10f * fraction
            }
            BackAnimationMode.SCALE -> {
                target.scaleX = 1f - 0.09f * fraction
                target.scaleY = 1f - 0.09f * fraction
                target.alpha = 1f - 0.16f * fraction
            }
            BackAnimationMode.CLASSIC -> {
                target.translationX = target.width * 0.04f * fraction
            }
            BackAnimationMode.NONE,
            BackAnimationMode.AOSP -> Unit
        }
    }

    private fun cancelBackGestureAnimation() {
        backGestureTarget?.animate()?.translationX(0f)?.translationY(0f)?.scaleX(1f)?.scaleY(1f)?.alpha(1f)
            ?.setDuration(170)?.setInterpolator(android.view.animation.DecelerateInterpolator())?.start()
        backGestureTarget = null
    }

    private fun commitBackGestureAnimation() {
        val target = backGestureTarget
        backGestureTarget = null
        when (backAnimationMode) {
            BackAnimationMode.MIUIX,
            BackAnimationMode.SCALE -> target?.animate()
                ?.translationX(0f)?.translationY(0f)?.scaleX(1f)?.scaleY(1f)?.alpha(1f)
                ?.setDuration(60)?.withEndAction { performBackForCurrentSurface() }?.start()
                ?: performBackForCurrentSurface()
            BackAnimationMode.CLASSIC -> target?.animate()
                ?.alpha(0.70f)?.translationX(target.width * 0.10f)?.setDuration(110)
                ?.withEndAction {
                    target.alpha = 1f
                    target.translationX = 0f
                    target.translationY = 0f
                    target.scaleX = 1f
                    target.scaleY = 1f
                    performBackForCurrentSurface()
                }?.start() ?: performBackForCurrentSurface()
            BackAnimationMode.NONE,
            BackAnimationMode.AOSP -> performBackForCurrentSurface()
        }
    }

    private fun performBackForCurrentSurface() {
        when {
            ::appInfoOverlay.isInitialized && appInfoOverlay.parent != null -> hideAppInfoPage()
            ::downloadConfirmOverlay.isInitialized && downloadConfirmOverlay.parent != null -> hideDownloadConfirmDialog()
            ::downloadsOverlay.isInitialized && downloadsOverlay.parent != null -> hideDownloadsOverlay()
            ::settingsOverlay.isInitialized && settingsOverlay.parent != null -> hideSettings()
            ::tabToolsOverlay.isInitialized && tabToolsOverlay.parent != null -> hideTabTools()
            ::tabChooserOverlay.isInitialized && tabChooserOverlay.parent != null -> hideTabChooser()
            else -> handleBrowserBack()
        }
        root.post { updateBrowserBackCallback() }
    }

    private fun handleBrowserBack() {
        val tab = activeTab ?: run {
            finish()
            return
        }
        when {
            !tab.isHome && tab.webView.canGoBack() -> tab.webView.goBack()
            !tab.isHome -> showHome(tab)
            tabs.size > 1 -> closeTab(tab.id)
            else -> finish()
        }
        root.post { updateBrowserBackCallback() }
    }

    private fun goBack() {
        val tab = activeTab ?: return
        if (!tab.isHome && tab.webView.canGoBack()) tab.webView.goBack() else if (!tab.isHome) showHome(tab)
    }

    private fun goForward() {
        activeTab?.webView?.takeIf { !it.url.isNullOrBlank() && it.canGoForward() }?.goForward()
    }

    private fun closeTab(tabId: Long) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index == -1) return
        val wasActive = activeTabId == tabId
        val removed = tabs.removeAt(index)
        removed.webView.stopLoading()
        removed.webView.destroy()
        if (tabs.isEmpty()) {
            createTab()
        } else if (wasActive) {
            selectTab(tabs.getOrElse(index) { tabs.last() }.id)
        } else {
            refreshTabStrip()
        }
        updateBrowserBackCallback()
    }

    private fun refreshTabStrip() {
        if (tabLayoutMode == TabLayoutMode.MINIMAL) {
            if (::tabCountButton.isInitialized) {
                tabCountButton.text = "▣ ${tabs.size}"
                tabCountButton.contentDescription = "切换标签页，当前 ${tabs.size} 个标签页"
            }
            return
        }
        if (!::tabStrip.isInitialized) return
        val palette = currentPalette()
        tabStrip.removeAllViews()
        tabs.forEach { tab ->
            val selected = tab.id == activeTabId
            // 材质：透明 tab 栏 — 毛玻璃底座直接透出，无需叠层
            // 仅用极细边框标识可交互区域，不堆叠透光层
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(9), 0, dp(2), 0)
                // 标签为低透明度磨砂芯片，选中标签略加深但仍显示网页纹理。
                background = roundedBackground(
                    if (selected) frostedSurfaceColor() else Color.argb(if (isDarkPalette()) 58 else 72, 255, 255, 255),
                    dp(13),
                    if (selected) palette.cardStroke else Color.argb(40, 180, 180, 184)
                )
                contentDescription = "${tab.title} 标签页"
                setOnClickListener { selectTab(tab.id) }
            }
            chip.addView(TextView(this).apply {
                text = trimTabTitle(if (tab.isHome) "主页" else tab.title)
                maxLines = 1
                textSize = 12f
                setTextColor(if (selected) palette.text else palette.mutedText)
            })
            chip.addView(TextView(this).apply {
                text = "×"
                textSize = 16f
                gravity = Gravity.CENTER
                contentDescription = "关闭 ${tab.title} 标签页"
                setTextColor(palette.icon)
                setOnClickListener { closeTab(tab.id) }
            }, LinearLayout.LayoutParams(dp(22), dp(30)).apply { marginStart = dp(2) })
            tabStrip.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)).apply {
                marginEnd = dp(4)
            })
        }
    }

    private fun showTabTools(anchor: View) {
        if (::tabToolsOverlay.isInitialized && tabToolsOverlay.parent != null) {
            hideTabTools()
            return
        }
        val palette = currentPalette()
        // 在覆盖层创建前记录触发按钮中心，后续布局完成后用同一屏幕坐标作为动画原点。
        tabToolsAnchorPoint = captureAnchor(anchor)
        // Apple Design 层级原则：模糊层与内容层分离
        // 模糊层在底层，工具栏浮于其上且不被模糊
        tabToolsOverlay = FrameLayout(this).apply {
            isClickable = true
            alpha = 0f
            // 材质：半透明遮罩营造景深，不使用模糊
            setBackgroundColor(Color.argb(45, 0, 0, 0))
            setOnClickListener { hideTabTools() }
        }
        val toolbar = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            // 材质：层级深度 - 工具栏浮起高于底部栏
            cardElevation = dp(16).toFloat()
            translationZ = dp(8).toFloat()
            // 材质：纯白厚毛玻璃浮动层
            setCardBackgroundColor(palette.card)
            strokeColor = palette.cardStroke
            strokeWidth = dp(1)
            alpha = 0f
            // 真实起点在 post 布局时计算：菜单将折叠到三小点按钮中心，而非固定向下 30dp。
            scaleX = 0.16f
            scaleY = 0.16f
            setOnClickListener { /* 点击菜单自身不关闭。 */ }

            // 工具栏内容：垂直布局
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(3), dp(8), dp(8))

                // 功能按钮：2行4列
                addView(buildTabToolRow(
                    tabToolTile("☆", "添加书签") { toggleBookmark(); hideTabTools() },
                    tabToolTile("⇩", "下载") { hideTabTools { showDownloadsOverlay() } },
                    tabToolTile("↗", "分享") { shareCurrentUrl(); hideTabTools() },
                    tabToolTile("⧉", "复制") { copyCurrentUrl(); hideTabTools() }
                ), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(48)
                ))
                addView(buildTabToolRow(
                    tabToolTile("⌫", "清缓存") { clearCurrentPageCache(); hideTabTools() },
                    tabToolTile("⌂", "主页") { activeTab?.let(::showHome); hideTabTools() },
                    tabToolTile("↻", "刷新") { activeTab?.webView?.reload(); hideTabTools() },
                    tabToolTile("⚙", "设置") { settingsButton ->
                        val point = captureAnchor(settingsButton)
                        hideTabTools { showSettings(point) }
                    }
                ), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(48)
                ).apply { topMargin = dp(9) })

            })
        }

        tabToolsOverlay.addView(
            toolbar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                setMargins(dp(8), 0, dp(8), dp(if (tabLayoutMode == TabLayoutMode.FULL) 106 else 62))
            }
        )
        root.addView(
            tabToolsOverlay,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        updateBrowserBackCallback()
        // 流畅交互：遮罩淡入
        tabToolsOverlay.animate().alpha(1f).setDuration(200).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        toolbar.post {
            val toolbarLocation = IntArray(2)
            toolbar.getLocationOnScreen(toolbarLocation)
            val anchorPoint = tabToolsAnchorPoint
                ?: PointF(toolbarLocation[0] + toolbar.width / 2f, toolbarLocation[1] + toolbar.height.toFloat())
            // 将 pivot 放在三小点相对于菜单的位置；纵向锚点位于菜单外时贴到最接近的菜单边缘。
            toolbar.pivotX = (anchorPoint.x - toolbarLocation[0]).coerceIn(0f, toolbar.width.toFloat())
            toolbar.pivotY = (anchorPoint.y - toolbarLocation[1]).coerceIn(0f, toolbar.height.toFloat())
            // 缩小后的 pivot 精确落回三小点中心；展开与关闭共享这对位移，保证“从哪来、回哪去”。
            tabToolsStartTranslationX = anchorPoint.x - (toolbarLocation[0] + toolbar.pivotX)
            tabToolsStartTranslationY = anchorPoint.y - (toolbarLocation[1] + toolbar.pivotY)
            toolbar.translationX = tabToolsStartTranslationX
            toolbar.translationY = tabToolsStartTranslationY
            toolbar.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .translationX(0f).translationY(0f)
                .setDuration(240)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.08f))
                .start()
            // 菜单内容在容器展开后轻微错开出现，避免视觉上先于容器出现。
            val contentView = toolbar.getChildAt(0) as? LinearLayout
            contentView?.let { container ->
                for (i in 0 until container.childCount) {
                    val row = container.getChildAt(i)
                    row.alpha = 0f
                    row.translationY = dp(10).toFloat()
                    row.postDelayed({ springAppear(row, 0.8f) }, 70L + i * 50L)
                }
            }
        }
    }

    private fun tabToolTile(symbol: String, description: String, onClick: (View) -> Unit): MaterialButton {
        val palette = currentPalette()
        return MaterialButton(this).apply {
            text = "$symbol\n$description"
            textSize = 11f
            isAllCaps = false
            contentDescription = description
            minWidth = 0
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            setPadding(dp(4), dp(4), dp(4), dp(4))
            cornerRadius = dp(14)
            // 材质：工具栏按钮 — 实体白，不叠在透光层上
            backgroundTintList = ColorStateList.valueOf(palette.group)
            setTextColor(palette.icon)
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            // 流畅交互：按压反馈
            applyPressButtonFeedback(this)
            setOnClickListener { onClick(it) }
        }
    }

    private fun buildTabToolRow(vararg buttons: MaterialButton): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            buttons.forEach { addView(it) }
        }
    }

    // --- 流畅交互：弹簧动画（使用 ViewPropertyAnimator + OvershootInterpolator） ---
    private fun springAppear(view: View, damping: Float = 0.7f) {
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration((200 + (1f - damping) * 100).toLong())
            .setInterpolator(android.view.animation.OvershootInterpolator(1.0f + damping * 0.4f))
            .start()
    }

    private fun springDismiss(view: View, targetScale: Float = 0.86f, targetY: Int = 30, damping: Float = 0.85f) {
        view.animate().alpha(0f).scaleX(targetScale).scaleY(targetScale).translationY(dp(targetY).toFloat())
            .setDuration((160 + (1f - damping) * 60).toLong())
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .start()
    }

    // --- 流畅交互：按压反馈 ---
    private fun applyPressFeedback(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80)
                        .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.2f)).start()
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                }
            }
            false
        }
    }

    // 按压反馈（不触发点击，配合已有 onClickListener）
    private fun applyPressButtonFeedback(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // 按压下沉
                    v.animate().scaleX(0.95f).scaleY(0.95f)
                        .setDuration(80)
                        .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    // 释放浮起
                    v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(120)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.2f)).start()
                }
            }
            false
        }
    }

    private fun hideTabTools(after: () -> Unit = {}) {
        if (!::tabToolsOverlay.isInitialized || tabToolsOverlay.parent == null) {
            after()
            return
        }
        val toolbar = tabToolsOverlay.getChildAt(0)
        // 与打开路径严格反向：菜单收缩并回到最初三小点按钮的中心。
        tabToolsOverlay.animate().alpha(0f).setDuration(180).setInterpolator(android.view.animation.AccelerateInterpolator()).start()
        toolbar.animate().alpha(0f).scaleX(0.16f).scaleY(0.16f)
            .translationX(tabToolsStartTranslationX).translationY(tabToolsStartTranslationY)
            .setDuration(190)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .start()
        // 延迟移除视图（等待动画基本完成）
        toolbar.postDelayed({
            if (tabToolsOverlay.parent != null) root.removeView(tabToolsOverlay)
            tabToolsAnchorPoint = null
            tabToolsStartTranslationX = 0f
            tabToolsStartTranslationY = 0f
            updateBrowserBackCallback()
            after()
        }, 200L)
    }

    private fun enqueueWebDownload(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        if (url.isBlank()) {
            toast("下载地址无效")
            return
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingLegacyDownload = PendingDownload(url, userAgent, contentDisposition, mimeType)
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), LEGACY_STORAGE_PERMISSION_REQUEST_CODE)
            return
        }
        // 显示下载确认弹窗（文件名、大小、下载器选择）
        showDownloadConfirmDialog(url, userAgent, contentDisposition, mimeType)
    }

    private fun showDownloadConfirmDialog(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val palette = currentPalette()

        if (::downloadConfirmOverlay.isInitialized && downloadConfirmOverlay.parent != null) return
        downloadConfirmOverlay = FrameLayout(this).apply {
            // 材质：模态遮罩 — 更暗的 scrim（150/255 不透明黑）聚焦前景
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            alpha = 0f
            elevation = dp(45).toFloat()
            isClickable = true
        }

        downloadConfirmDialog = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            cardElevation = dp(16).toFloat()
            // 材质：模态弹窗 — 纯白厚毛玻璃
            setCardBackgroundColor(palette.card)
            strokeColor = palette.cardStroke
            strokeWidth = dp(1)
            alpha = 0f
            scaleX = 0.92f
            scaleY = 0.92f
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(20), dp(24), dp(20))

                // 标题
                addView(TextView(this@MainActivity).apply {
                    text = "下载文件"
                    textSize = 20f
                    setTextColor(palette.text)
                })
                // 文件名
                addView(TextView(this@MainActivity).apply {
                    text = fileName
                    textSize = 15f
                    maxLines = 2
                    setTextColor(palette.text)
                    setPadding(0, dp(12), 0, dp(0))
                })
                // 文件大小提示
                addView(TextView(this@MainActivity).apply {
                    text = "正在获取文件信息..."
                    textSize = 13f
                    setTextColor(palette.mutedText)
                    setPadding(0, dp(4), 0, dp(16))
                })

                // 下载器选择
                addView(TextView(this@MainActivity).apply {
                    text = "选择下载方式"
                    textSize = 14f
                    setTextColor(palette.mutedText)
                    setPadding(0, dp(0), 0, dp(8))
                })

                // Android 下载器按钮
                addView(MaterialButton(this@MainActivity).apply {
                    text = "系统下载管理器"
                    textSize = 15f
                    isAllCaps = false
                    gravity = Gravity.CENTER
                    insetTop = 0
                    insetBottom = 0
                    cornerRadius = dp(14)
                    // 材质：纯灰白文本，无蓝调
                    setTextColor(if (isDarkPalette()) Color.rgb(235, 235, 235) else Color.WHITE)
                    backgroundTintList = ColorStateList.valueOf(palette.accent)
                    setPadding(dp(16), 0, dp(16), 0)
                    setOnClickListener {
                        hideDownloadConfirmDialog()
                        enqueueWebDownloadWithManager(url, userAgent, contentDisposition, mimeType)
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(8) })

                // 自带下载器按钮
                addView(MaterialButton(this@MainActivity).apply {
                    text = "应用内下载"
                    textSize = 15f
                    isAllCaps = false
                    gravity = Gravity.CENTER
                    insetTop = 0
                    insetBottom = 0
                    cornerRadius = dp(14)
                    setTextColor(palette.text)
                    backgroundTintList = ColorStateList.valueOf(palette.group)
                    setPadding(dp(16), 0, dp(16), 0)
                    setOnClickListener {
                        hideDownloadConfirmDialog()
                        enqueueWebDownloadBuiltIn(url, userAgent, contentDisposition, mimeType, fileName)
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(12) })

                // 取消按钮
                addView(MaterialButton(this@MainActivity).apply {
                    text = "取消"
                    textSize = 14f
                    isAllCaps = false
                    gravity = Gravity.CENTER
                    minWidth = 0
                    minHeight = 0
                    insetTop = 0
                    insetBottom = 0
                    cornerRadius = dp(14)
                    setTextColor(palette.mutedText)
                    backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    setOnClickListener {
                        hideDownloadConfirmDialog()
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))
            })
        }

        downloadConfirmOverlay.addView(downloadConfirmDialog, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply { setMargins(dp(32), 0, dp(32), 0) })

        // 点击关闭
        downloadConfirmOverlay.setOnClickListener {
            hideDownloadConfirmDialog()
        }

        root.addView(downloadConfirmOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        downloadConfirmOverlay.bringToFront()
        updateBrowserBackCallback()

        downloadConfirmOverlay.animate().alpha(1f).setDuration(180).start()
        downloadConfirmDialog.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    private fun hideDownloadConfirmDialog(after: () -> Unit = {}) {
        if (!::downloadConfirmOverlay.isInitialized || downloadConfirmOverlay.parent == null) {
            after()
            return
        }
        downloadConfirmOverlay.animate().alpha(0f).setDuration(150).withEndAction {
            if (downloadConfirmOverlay.parent != null) root.removeView(downloadConfirmOverlay)
            updateBrowserBackCallback()
            after()
        }.start()
    }

    private fun enqueueWebDownloadWithManager(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("来自浮悬浏览器")
            if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            if (userAgent.isNotBlank()) addRequestHeader("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
        }
        try {
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = manager.enqueue(request)
            toast("已开始下载：$fileName")
        } catch (error: Exception) {
            toast("无法开始下载：${error.message ?: "系统下载服务不可用"}")
        }
    }

    private fun enqueueWebDownloadBuiltIn(url: String, userAgent: String, contentDisposition: String?, mimeType: String?, fileName: String) {
        toast("应用内下载：$fileName")
        // 使用 DownloadManager 但由应用自己管理进度（简化实现）
        // 实际项目中可用 OkHttp/HttpURLConnection 实现内置下载
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("应用内下载 · 来自浮悬浏览器")
            if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            if (userAgent.isNotBlank()) addRequestHeader("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            setRequiresCharging(false)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        try {
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = manager.enqueue(request)
            DownloadStore.add(this, downloadId)
            // 浏览器内置下载器以真实 DownloadManager 任务为数据源，离开应用后仍通过前台服务更新。
            startProgressNotificationForDownload(downloadId)
            toast("已开始下载：$fileName")
        } catch (error: Exception) {
            toast("无法开始下载：${error.message ?: "下载服务不可用"}")
        }
    }

    private fun enqueueWebDownloadInternal(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("来自浮悬浏览器")
            if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            if (userAgent.isNotBlank()) addRequestHeader("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
        }
        try {
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = manager.enqueue(request)
            DownloadStore.add(this, downloadId)
            startProgressNotificationForDownload(downloadId)
            toast("已开始下载：$fileName")
        } catch (error: Exception) {
            toast("无法开始下载：${error.message ?: "系统下载服务不可用"}")
        }
    }

    private fun startProgressNotificationForDownload(downloadId: Long) {
        if (!downloadNotificationsEnabled) {
            pendingNotificationDownloadIds.add(downloadId)
            DownloadNotificationDiagnostics.record(this, "内置下载已开始，正在请求启用下载实时通知")
            requestDownloadNotificationPermission()
            return
        }
        if (hasDownloadNotificationPermission()) {
            val probePosted = DownloadNotificationDiagnostics.postDownloadProbe(this, downloadId)
            if (!probePosted) {
                toast("下载通知探针未发布，请在下载管理查看诊断信息")
                return
            }
            try {
                DownloadProgressService.start(this, downloadId)
            } catch (error: Exception) {
                DownloadNotificationDiagnostics.record(
                    this,
                    "前台进度服务启动请求失败：${error.javaClass.simpleName}: ${error.message ?: "未知错误"}"
                )
                toast("前台进度服务未启动，请在下载管理查看诊断信息")
            }
        } else {
            pendingNotificationDownloadIds.add(downloadId)
            DownloadNotificationDiagnostics.record(this, "下载已开始，正在请求系统通知权限")
            requestDownloadNotificationPermission()
        }
    }

    private fun requestDownloadNotificationPermission() {
        if (hasDownloadNotificationPermission()) {
            setDownloadNotificationsEnabled(true)
            val ids = pendingNotificationDownloadIds.toList()
            pendingNotificationDownloadIds.clear()
            ids.forEach(::startProgressNotificationForDownload)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
        } else {
            toast("系统通知当前已关闭，请在系统通知设置中开启")
            openNotificationSettings()
        }
    }

    private fun hasDownloadNotificationPermission(): Boolean {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeGranted && androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun setDownloadNotificationsEnabled(enabled: Boolean) {
        downloadNotificationsEnabled = enabled
        preferences.edit().putBoolean(KEY_DOWNLOAD_NOTIFICATIONS, enabled).apply()
        if (!enabled) {
            pendingNotificationDownloadIds.clear()
            stopService(Intent(this, DownloadProgressService::class.java))
            DownloadNotificationDiagnostics.cancelProgress(this)
            DownloadNotificationDiagnostics.record(this, "用户已关闭下载实时通知")
        }
    }

    private fun openNotificationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            })
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun openLiveUpdateSettings() {
        if (Build.VERSION.SDK_INT < 36) {
            toast("当前 Android 版本不支持系统实时动态通知，将使用标准进度通知")
            return
        }
        try {
            startActivity(Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            })
        } catch (_: ActivityNotFoundException) {
            openNotificationSettings()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LEGACY_STORAGE_PERMISSION_REQUEST_CODE -> {
                val download = pendingLegacyDownload
                pendingLegacyDownload = null
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && download != null) {
                    enqueueWebDownloadInternal(download.url, download.userAgent, download.contentDisposition, download.mimeType)
                } else {
                    toast("未授予存储权限，无法开始下载")
                }
            }
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && hasDownloadNotificationPermission()
                setDownloadNotificationsEnabled(granted)
                if (granted) {
                    val ids = pendingNotificationDownloadIds.toList()
                    pendingNotificationDownloadIds.clear()
                    ids.forEach(::startProgressNotificationForDownload)
                    toast("已开启下载实时通知")
                } else {
                    pendingNotificationDownloadIds.clear()
                    toast("通知权限未授予；下载仍会继续，可在系统通知设置中重新开启")
                }
            }
        }
    }

    private fun showDownloadsOverlay() {
        if (::downloadsOverlay.isInitialized && downloadsOverlay.parent != null) return
        val palette = currentPalette()
        downloadSelectionMode = false
        selectedDownloadIds.clear()
        downloadsOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            alpha = 0f
            elevation = dp(40).toFloat()
            isClickable = true
            setOnClickListener { hideDownloadsOverlay() }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(18))
        }
        lateinit var selectionBar: LinearLayout
        lateinit var refreshDownloads: () -> Unit
        refreshDownloads = {
            content.removeAllViews()
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val ids = DownloadStore.ids(this)
            if (ids.isEmpty()) {
                addDownloadMessage(content, "暂无下载任务。网页触发下载后会显示在这里。")
            } else {
                var visibleCount = 0
                ids.forEach { id ->
                    val cursor = manager.query(DownloadManager.Query().setFilterById(id))
                    cursor?.use {
                        if (!it.moveToFirst()) {
                            DownloadStore.remove(this, id)
                            selectedDownloadIds.remove(id)
                            downloadSpeedSamples.remove(id)
                            return@use
                        }
                        visibleCount += 1
                        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val systemTitle = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                            .orEmpty().ifBlank { "下载文件" }
                        val title = DownloadStore.displayName(this, id) ?: systemTitle
                        val received = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val progress = if (total > 0L) ((received * 100L) / total).toInt().coerceIn(0, 100) else null
                        val speed = if (status == DownloadManager.STATUS_RUNNING) updateDownloadSpeed(id, received) else {
                            downloadSpeedSamples.remove(id)
                            null
                        }
                        val detail = downloadDetail(status, received, total, progress, speed)
                        val row = downloadRow(
                            id = id,
                            title = title,
                            detail = detail,
                            selected = selectedDownloadIds.contains(id),
                            selectionMode = downloadSelectionMode,
                            status = status
                        )
                        content.addView(row, LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dp(8) })
                    }
                }
                if (visibleCount == 0) addDownloadMessage(content, "暂无可显示的下载任务。")
            }
            updateSelectionBar(selectionBar, refreshDownloads)
        }
        val sheet = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            cardElevation = dp(18).toFloat()
            setCardBackgroundColor(palette.card)
            strokeColor = palette.cardStroke
            strokeWidth = dp(1)
            alpha = 0f
            scaleX = 0.9f
            scaleY = 0.9f
            setOnClickListener { /* 列表区域不关闭。 */ }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(18), dp(12), dp(10), dp(4))
                    addView(TextView(this@MainActivity).apply {
                        text = "下载"
                        textSize = 22f
                        setTextColor(palette.text)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    // 右上角仅保留关闭按钮；列表会自动每秒刷新，因此不再需要刷新按钮。
                    addView(MaterialButton(this@MainActivity).apply {
                        text = "×"
                        textSize = 22f
                        contentDescription = "关闭下载管理"
                        minWidth = 0
                        minHeight = 0
                        insetTop = 0
                        insetBottom = 0
                        setTextColor(palette.icon)
                        backgroundTintList = ColorStateList.valueOf(palette.group)
                        setOnClickListener { hideDownloadsOverlay() }
                    }, LinearLayout.LayoutParams(dp(42), dp(38)))
                })
                addView(ScrollView(this@MainActivity).apply { addView(content) }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ))
                selectionBar = LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(8), dp(14), dp(12))
                }
                addView(selectionBar, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            })
        }
        downloadsOverlay.addView(sheet, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.70f).roundToInt(),
            Gravity.CENTER
        ).apply { setMargins(dp(20), dp(48), dp(20), dp(48)) })
        root.addView(downloadsOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        downloadsOverlay.bringToFront()
        updateBrowserBackCallback()
        downloadsRefreshAction = refreshDownloads
        refreshDownloads()
        downloadsRefreshHandler.removeCallbacks(downloadsRefreshRunnable)
        downloadsRefreshHandler.postDelayed(downloadsRefreshRunnable, 1_000L)
        downloadsOverlay.animate().alpha(1f).setDuration(160).start()
        sheet.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(210)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    private fun hideDownloadsOverlay(after: () -> Unit = {}) {
        if (!::downloadsOverlay.isInitialized || downloadsOverlay.parent == null) {
            after()
            return
        }
        downloadsRefreshHandler.removeCallbacks(downloadsRefreshRunnable)
        downloadsRefreshAction = null
        val sheet = downloadsOverlay.getChildAt(0)
        sheet.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(150).withEndAction {
            if (downloadsOverlay.parent != null) root.removeView(downloadsOverlay)
            selectedDownloadIds.clear()
            downloadSelectionMode = false
            updateBrowserBackCallback()
            after()
        }.start()
    }

    private fun updateSelectionBar(bar: LinearLayout, refresh: () -> Unit) {
        bar.removeAllViews()
        val palette = currentPalette()
        bar.addView(downloadActionButton(
            if (downloadSelectionMode) "取消多选" else "多选",
            if (downloadSelectionMode) "取消文件多选" else "选择多个下载文件"
        ) {
            downloadSelectionMode = !downloadSelectionMode
            if (!downloadSelectionMode) selectedDownloadIds.clear()
            refresh()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))
        if (downloadSelectionMode) {
            bar.addView(TextView(this).apply {
                text = "已选 ${selectedDownloadIds.size} 项"
                textSize = 13f
                setTextColor(palette.mutedText)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
            }, LinearLayout.LayoutParams(0, dp(40), 1f))
            bar.addView(downloadActionButton("删除", "删除已选择的文件") {
                if (selectedDownloadIds.isEmpty()) {
                    toast("请先选择文件")
                } else {
                    showBatchDeleteConfirmation(selectedDownloadIds.toList(), refresh)
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))
        } else {
            bar.addView(View(this), LinearLayout.LayoutParams(0, dp(40), 1f))
        }
    }

    private fun updateDownloadSpeed(id: Long, bytes: Long): Long? {
        val now = SystemClock.elapsedRealtime()
        val previous = downloadSpeedSamples[id]
        downloadSpeedSamples[id] = DownloadSpeedSample(bytes, now)
        if (previous == null || now <= previous.atMillis || bytes < previous.bytes) return null
        return ((bytes - previous.bytes) * 1_000L / (now - previous.atMillis)).coerceAtLeast(0L)
    }

    private fun downloadDetail(status: Int, received: Long, total: Long, progress: Int?, speed: Long?): String = when (status) {
        DownloadManager.STATUS_PENDING -> "等待下载"
        DownloadManager.STATUS_RUNNING -> buildString {
            append(progress?.let { "正在下载 · $it%" } ?: "正在下载")
            if (total > 0L) append(" · ${formatDownloadBytes(received)} / ${formatDownloadBytes(total)}")
            append(" · ${formatDownloadSpeed(speed)}")
        }
        DownloadManager.STATUS_PAUSED -> "下载已暂停"
        DownloadManager.STATUS_SUCCESSFUL -> "下载完成 · ${formatDownloadBytes(total.takeIf { it > 0L } ?: received)}"
        DownloadManager.STATUS_FAILED -> "下载失败"
        else -> "下载状态未知"
    }

    private fun formatDownloadBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun formatDownloadSpeed(speed: Long?): String = speed?.let { "${formatDownloadBytes(it)}/s" } ?: "计算速度中"

    private fun addDownloadMessage(container: LinearLayout, message: String) {
        container.addView(TextView(this).apply {
            text = message
            textSize = 15f
            setTextColor(currentPalette().mutedText)
            setPadding(dp(8), dp(24), dp(8), dp(8))
        })
    }

    private fun downloadRow(
        id: Long,
        title: String,
        detail: String,
        selected: Boolean,
        selectionMode: Boolean,
        status: Int
    ): LinearLayout {
        val palette = currentPalette()
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(8), dp(10))
            background = roundedBackground(
                if (selected) palette.selectedChip else palette.group,
                dp(16),
                palette.cardStroke
            )
            if (selectionMode) {
                addView(CheckBox(this@MainActivity).apply {
                    isChecked = selected
                    contentDescription = "选择 $title"
                    buttonTintList = ColorStateList.valueOf(palette.accent)
                    setOnClickListener {
                        if (isChecked) selectedDownloadIds.add(id) else selectedDownloadIds.remove(id)
                        downloadsRefreshAction?.invoke()
                    }
                }, LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 15f
                    maxLines = 2
                    setTextColor(palette.text)
                })
                addView(TextView(this@MainActivity).apply {
                    text = detail
                    textSize = 13f
                    setTextColor(palette.mutedText)
                    setPadding(0, dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener {
                if (downloadSelectionMode) {
                    if (selectedDownloadIds.contains(id)) selectedDownloadIds.remove(id) else selectedDownloadIds.add(id)
                    downloadsRefreshAction?.invoke()
                } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    openDownloadedFile(id)
                }
            }
            setOnLongClickListener {
                showDownloadItemActions(it, id, title, status)
                true
            }
        }
    }

    private fun downloadActionButton(label: String, description: String, onClick: () -> Unit): MaterialButton {
        val palette = currentPalette()
        return MaterialButton(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            contentDescription = description
            minWidth = 0
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            setTextColor(palette.icon)
            backgroundTintList = ColorStateList.valueOf(palette.actionBackground)
            setOnClickListener { onClick() }
        }
    }

    private fun showDownloadItemActions(anchor: View, id: Long, title: String, status: Int) {
        PopupMenu(this, anchor).apply {
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                menu.add("打开").setOnMenuItemClickListener { openDownloadedFile(id); true }
                menu.add("分享").setOnMenuItemClickListener { shareDownloadedFile(id); true }
                menu.add("重命名").setOnMenuItemClickListener { showRenameDownloadDialog(id, title); true }
            }
            menu.add(if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) "取消并删除" else "删除")
                .setOnMenuItemClickListener { deleteDownloads(listOf(id)); downloadsRefreshAction?.invoke(); true }
            show()
        }
    }

    private fun showBatchDeleteConfirmation(ids: List<Long>, refresh: () -> Unit) {
        val deleteFiles = CheckBox(this).apply {
            text = "同时删除已完成的文件"
            textSize = 15f
            isChecked = false
            setPadding(dp(20), dp(4), dp(20), dp(4))
        }
        AlertDialog.Builder(this)
            .setTitle("删除 ${ids.size} 个下载任务？")
            .setMessage("不勾选时仅从本浏览器的下载列表移除任务，已完成文件会保留在设备中。进行中的任务会被取消。")
            .setView(deleteFiles)
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                deleteDownloads(ids, deleteFiles.isChecked)
                selectedDownloadIds.clear()
                refresh()
            }
            .show()
    }

    private fun deleteDownloads(ids: List<Long>, deleteFiles: Boolean = true) {
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var removedFiles = 0
        ids.forEach { id ->
            val status = downloadStatus(manager, id)
            // 仅删除任务时保留已完成文件；进行中、等待中和失败任务仍需从 DownloadManager 取消/移除。
            val removeFromSystem = deleteFiles || status != DownloadManager.STATUS_SUCCESSFUL
            if (removeFromSystem) {
                manager.remove(id)
                if (status == DownloadManager.STATUS_SUCCESSFUL && deleteFiles) removedFiles += 1
            }
            DownloadStore.remove(this, id)
            selectedDownloadIds.remove(id)
            downloadSpeedSamples.remove(id)
        }
        val message = if (deleteFiles) {
            "已删除 ${ids.size} 个下载任务${if (removedFiles > 0) "，并删除 $removedFiles 个文件" else ""}"
        } else {
            "已从列表移除 ${ids.size} 个下载任务，文件已保留"
        }
        toast(message)
    }

    private fun downloadStatus(manager: DownloadManager, id: Long): Int? {
        return manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) else null
        }
    }

    private fun shareDownloadedFile(id: Long) {
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = manager.getUriForDownloadedFile(id) ?: run {
            toast("文件尚不可分享")
            return
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "分享文件"))
    }

    private fun showRenameDownloadDialog(id: Long, currentTitle: String) {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(currentTitle)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("重命名下载")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isBlank()) {
                    toast("文件名不能为空")
                    return@setPositiveButton
                }
                renameDownload(id, newName)
                downloadsRefreshAction?.invoke()
            }
            .show()
    }

    private fun renameDownload(id: Long, newName: String) {
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = manager.getUriForDownloadedFile(id)
        val renamed = if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                }, null, null) > 0
            }.getOrDefault(false)
        } else false
        DownloadStore.setDisplayName(this, id, newName)
        toast(if (renamed) "文件已重命名" else "已更新下载列表名称")
    }

    private fun openDownloadedFile(id: Long) {
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = manager.getUriForDownloadedFile(id)
        if (uri == null) {
            toast("下载文件不可用")
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: ActivityNotFoundException) {
            toast("设备没有可打开此文件的应用")
        }
    }

    private fun toggleBookmark() {
        val tab = activeTab ?: return
        if (tab.isHome || tab.url.isBlank()) {
            toast("主页无需添加书签")
            return
        }
        val bookmarks = preferences.getStringSet(KEY_BOOKMARKS, emptySet()).orEmpty().toMutableSet()
        if (bookmarks.remove(tab.url)) toast("已从书签移除") else {
            bookmarks.add(tab.url)
            toast("已添加到书签")
        }
        preferences.edit().putStringSet(KEY_BOOKMARKS, bookmarks).apply()
    }

    private fun shareCurrentUrl() {
        val url = activeTab?.url?.takeIf { it.isNotBlank() } ?: run {
            toast("主页没有可分享的网址")
            return
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }, "分享网页"))
    }

    private fun copyCurrentUrl() {
        val url = activeTab?.url?.takeIf { it.isNotBlank() } ?: run {
            toast("主页没有可复制的网址")
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("网页地址", url))
        toast("网址已复制")
    }

    private fun clearCurrentPageCache() {
        activeTab?.webView?.clearCache(true)
        toast("当前页面缓存已清除")
    }

    private fun showSettings(anchorPoint: PointF? = null) {
        if (::settingsOverlay.isInitialized && settingsOverlay.parent != null) return
        settingsAnchorPoint = anchorPoint
        isSettingsClosing = false
        settingsOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            alpha = 0f
            elevation = dp(40).toFloat()
            isClickable = true
            setOnClickListener { hideSettings() }
        }
        settingsDialog = buildSettingsDialog().apply {
            alpha = 0f
            elevation = dp(48).toFloat()
            setOnClickListener { /* 阻止点击内容区关闭弹窗。 */ }
        }
        settingsOverlay.addView(
            settingsDialog,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.76f).roundToInt(),
                Gravity.CENTER
            ).apply { setMargins(dp(22), dp(38), dp(22), dp(38)) }
        )
        root.addView(settingsOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        settingsOverlay.bringToFront()
        updateBrowserBackCallback()
        settingsOverlay.animate().alpha(1f).setDuration(180).start()
        animateSettingsFromAnchor()
    }

    private fun animateSettingsFromAnchor() {
        settingsDialog.post {
            val dialogLocation = IntArray(2)
            settingsDialog.getLocationOnScreen(dialogLocation)
            val point = settingsAnchorPoint
            val anchorX = point?.x ?: dialogLocation[0] + settingsDialog.width / 2f
            val anchorY = point?.y ?: dialogLocation[1] + settingsDialog.height / 2f
            settingsDialog.pivotX = (anchorX - dialogLocation[0]).coerceIn(0f, settingsDialog.width.toFloat())
            settingsDialog.pivotY = (anchorY - dialogLocation[1]).coerceIn(0f, settingsDialog.height.toFloat())
            settingsStartTranslationX = anchorX - (dialogLocation[0] + settingsDialog.pivotX)
            settingsStartTranslationY = anchorY - (dialogLocation[1] + settingsDialog.pivotY)
            settingsDialog.apply {
                scaleX = 0.14f
                scaleY = 0.14f
                translationX = settingsStartTranslationX
                translationY = settingsStartTranslationY
            }
            settingsDialog.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .translationX(0f).translationY(0f).setDuration(260)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
    }

    private fun buildSettingsDialog(): MaterialCardView {
        val palette = currentPalette()
        return MaterialCardView(this).apply {
            radius = dp(28).toFloat()
            cardElevation = dp(18).toFloat()
            // 材质：设置弹窗 — 纯白厚毛玻璃
            setCardBackgroundColor(palette.card)
            strokeColor = palette.cardStroke
            strokeWidth = dp(1)
            addView(ScrollView(this@MainActivity).apply {
                isFillViewport = true
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20), dp(16), dp(20), dp(18))
                    addView(buildSettingsHeader())

                    // 搜索引擎设置
                    addView(settingsDescription("默认搜索引擎", "选择搜索或地址栏查询时使用的搜索引擎。"))
                    addView(settingsDivider())
                    addView(buildSearchEngineSelector())

                    addView(settingsDivider())
                    addView(settingsChoice("主题", ThemeMode.entries.map { it.label }, themeMode.label) { selected ->
                        themeMode = ThemeMode.entries.first { it.label == selected }
                        preferences.edit().putString(KEY_THEME_MODE, themeMode.key).apply()
                        applyAppearance()
                        refreshSettings()
                    })

                    addView(settingsDivider())
                    addView(settingsChoice("Tab 显示模式", TabLayoutMode.entries.map { it.label }, tabLayoutMode.label) { selected ->
                        tabLayoutMode = TabLayoutMode.entries.first { it.label == selected }
                        preferences.edit().putString(KEY_TAB_LAYOUT_MODE, tabLayoutMode.key).apply()
                        applyTabLayout()
                        refreshSettings()
                    })
                    if (tabLayoutMode == TabLayoutMode.FULL) {
                        addView(settingsDivider())
                        addView(settingsPercentageSlider(
                            "完整 Tab 圆角",
                            "调整包住完整 Tab 的长方形四角圆润程度。",
                            fullTabCornerPercent
                        ) { percent ->
                            fullTabCornerPercent = percent.coerceIn(0, 100)
                            preferences.edit().putInt(KEY_FULL_TAB_CORNER_PERCENT, fullTabCornerPercent).apply()
                            applyTabLayout()
                            refreshSettings()
                        })
                    }

                    addView(settingsDivider())
                    addView(settingsSwitch("启用 JavaScript", "允许网站运行交互脚本。", javascriptEnabled) { enabled ->
                        javascriptEnabled = enabled
                        preferences.edit().putBoolean(KEY_JAVASCRIPT_ENABLED, enabled).apply()
                        tabs.forEach(::applyWebSettings)
                    })

                    addView(settingsDivider())
                    addView(settingsChoice("返回手势样式", BackAnimationMode.entries.map { it.label }, backAnimationMode.label) { selected ->
                        backAnimationMode = BackAnimationMode.entries.first { it.label == selected }
                        preferences.edit().putString(KEY_BACK_ANIMATION_MODE, backAnimationMode.key).apply()
                        updateBrowserBackCallback()
                        // 立即重建选择控件，让点击的样式按钮直接呈现为已选中，而非只显示提示。
                        refreshSettings()
                    })

                    addView(settingsDivider())
                    addView(settingsAction("应用信息", "查看应用版本、源码仓库等信息。") {
                        showAppInfoPage()
                    })
                })
            })
        }
    }

    private fun buildSearchEngineSelector(): LinearLayout {
        val palette = currentPalette()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(0))
                // 预设搜索引擎选择按钮组
                searchEnginePresets.filter { it.key != "custom" }.forEach { engine ->
                    addView(MaterialButton(this@MainActivity).apply {
                        text = engine.name
                        textSize = 12f
                        isAllCaps = false
                        minWidth = 0
                        minHeight = 0
                        insetTop = 0
                        insetBottom = 0
                        cornerRadius = dp(15)
                        setPadding(dp(8), 0, dp(8), 0)
                        val active = searchEngineKey == engine.key
                        // 材质：搜索引擎按钮 — 实体白底座，选中时蓝色高亮
                        backgroundTintList = ColorStateList.valueOf(if (active) palette.selectedChip else palette.group)
                        setTextColor(if (active) palette.text else palette.mutedText)
                        setOnClickListener {
                            searchEngineKey = engine.key
                            preferences.edit().putString(KEY_SEARCH_ENGINE, engine.key).apply()
                            // 刷新设置页
                            refreshSettings()
                        }
                    }, LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) })
                }
            })
            // 自定义搜索引擎输入
            if (searchEngineKey == "custom") {
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(8), 0, 0)
                    val customInput = EditText(this@MainActivity).apply {
                        setSingleLine(true)
                        textSize = 14f
                        setTextColor(palette.text)
                        setHintTextColor(palette.mutedText)
                        hint = "https://example.com/search?q="
                        setText(customSearchUrl)
                        setPadding(dp(10), 0, dp(10), 0)
                        background = roundedBackground(palette.input, dp(12), palette.cardStroke)
                    }
                    addView(customInput, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) })
                    addView(MaterialButton(this@MainActivity).apply {
                        text = "保存"
                        textSize = 13f
                        isAllCaps = false
                        minWidth = 0
                        minHeight = 0
                        insetTop = 0
                        insetBottom = 0
                        cornerRadius = dp(12)
                        setTextColor(Color.WHITE)
                        backgroundTintList = ColorStateList.valueOf(palette.accent)
                        setOnClickListener {
                            val url = customInput.text.toString().trim()
                            if (url.isNotEmpty() && url.contains("{q}")) {
                                customSearchUrl = url
                                preferences.edit().putString(KEY_CUSTOM_SEARCH_URL, url).apply()
                                toast("已保存自定义搜索引擎")
                            } else if (url.isNotEmpty() && url.contains("q=")) {
                                // 兼容不含 {q} 的 URL
                                customSearchUrl = url
                                preferences.edit().putString(KEY_CUSTOM_SEARCH_URL, url).apply()
                                toast("已保存自定义搜索引擎")
                            } else {
                                toast("请输入有效的搜索 URL，包含 ?q= 或 {q}")
                            }
                        }
                    }, LinearLayout.LayoutParams(dp(60), dp(38)))
                })
            }
        }
    }

    private fun refreshSettings() {
        if (::settingsOverlay.isInitialized && settingsOverlay.parent != null) {
            root.removeView(settingsOverlay)
            settingsAnchorPoint?.let { showSettings(it) } ?: showSettings()
        }
    }

    private fun buildSettingsHeader(): LinearLayout {
        val palette = currentPalette()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "浏览器设置"
                textSize = 22f
                setTextColor(palette.text)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(MaterialButton(this@MainActivity).apply {
                text = "×"
                textSize = 22f
                contentDescription = "关闭设置"
                minWidth = 0
                minHeight = 0
                insetTop = 0
                insetBottom = 0
                setPadding(0, 0, 0, 0)
                cornerRadius = dp(15)
                // 材质：设置关闭按钮 — 实体白交互元素，置于毛玻璃之上
                backgroundTintList = ColorStateList.valueOf(palette.group)
                setTextColor(palette.icon)
                setOnClickListener { hideSettings() }
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
        }
    }

    private fun settingsChoice(
        title: String,
        options: List<String>,
        selected: String,
        onSelected: (String) -> Unit
    ): LinearLayout {
        val palette = currentPalette()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 16f
                setTextColor(palette.text)
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
                options.forEach { option ->
                    addView(MaterialButton(this@MainActivity).apply {
                        text = option
                        textSize = 13f
                        isAllCaps = false
                        minWidth = 0
                        minHeight = 0
                        insetTop = 0
                        insetBottom = 0
                        cornerRadius = dp(15)
                        setPadding(dp(9), 0, dp(9), 0)
                        val active = option == selected
                        backgroundTintList = ColorStateList.valueOf(if (active) palette.selectedChip else palette.group)
                        setTextColor(if (active) palette.text else palette.mutedText)
                        setOnClickListener { onSelected(option) }
                    }, LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(5) })
                }
            })
        }
    }

    private fun settingsPercentageSlider(
        title: String,
        summary: String,
        percent: Int,
        onChanged: (Int) -> Unit
    ): LinearLayout {
        val palette = currentPalette()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            val header = LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(settingsText(title, summary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(this@MainActivity).apply {
                    text = "${percent.coerceIn(0, 100)}%"
                    textSize = 15f
                    setTextColor(palette.accent)
                    gravity = Gravity.CENTER_VERTICAL
                }.also { value ->
                    tag = value
                }, LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            addView(header)
            val percentageLabel = header.tag as TextView
            addView(SeekBar(this@MainActivity).apply {
                max = 100
                progress = percent.coerceIn(0, 100)
                progressTintList = ColorStateList.valueOf(palette.accent)
                thumbTintList = ColorStateList.valueOf(palette.accent)
                contentDescription = "$title：${progress}%"
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                        percentageLabel.text = "${value.coerceIn(0, 100)}%"
                        contentDescription = "$title：${value.coerceIn(0, 100)}%"
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        onChanged(seekBar?.progress?.coerceIn(0, 100) ?: percent.coerceIn(0, 100))
                    }
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)).apply {
                topMargin = dp(4)
            })
        }
    }

    private fun settingsSwitch(title: String, summary: String, checked: Boolean, onChanged: (Boolean) -> Unit): LinearLayout {
        val palette = currentPalette()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            addView(settingsText(title, summary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(SwitchCompat(this@MainActivity).apply {
                isChecked = checked
                contentDescription = title
                thumbTintList = ColorStateList.valueOf(palette.accent)
                setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
            })
        }
    }

    private fun settingsAction(title: String, summary: String, action: () -> Unit): MaterialButton {
        val palette = currentPalette()
        return MaterialButton(this).apply {
            text = "$title\n$summary"
            textSize = 15f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            isAllCaps = false
            setTextColor(palette.text)
            backgroundTintList = ColorStateList.valueOf(palette.actionBackground)
            cornerRadius = dp(16)
            insetTop = 0
            insetBottom = 0
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { action() }
        }
    }

    private fun settingsDescription(title: String, summary: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(14))
            addView(settingsText(title, summary))
        }
    }

    private fun settingsText(title: String, summary: String): LinearLayout {
        val palette = currentPalette()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 16f
                setTextColor(palette.text)
            })
            addView(TextView(this@MainActivity).apply {
                text = summary
                textSize = 13f
                setTextColor(palette.mutedText)
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun settingsDivider(): View {
        // 材质：柔和的纯灰分隔线（低视觉权重）
        return View(this).apply {
            setBackgroundColor(currentPalette().divider)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        }
    }

    private fun applyAppearance() {
        // 刷新整个界面以应用新主题
        val palette = currentPalette()
        root.setBackgroundColor(palette.page)
        // 重新构建底部控件
        attachBottomControls()
        // 刷新 web 容器内当前内容
        activeTab?.let {
            if (it.isHome) {
                webContainer.removeAllViews()
                webContainer.addView(buildHomeScreen(it))
            }
        }
        // 刷新设置弹窗颜色
        if (::settingsDialog.isInitialized && settingsDialog.parent != null) {
            settingsDialog.setCardBackgroundColor(palette.card)
            settingsDialog.strokeColor = palette.cardStroke
        }
    }

    private fun applyTabLayout() {
        attachBottomControls()
        val contentParams = webContainer.layoutParams as FrameLayout.LayoutParams
        contentParams.bottomMargin = 0
        webContainer.layoutParams = contentParams
        activeTab?.let { renderActiveTab(it) }
        refreshTabStrip()
        scheduleFrostedBackdropRefresh()
    }

    private fun hideSettings() {
        if (!::settingsOverlay.isInitialized || settingsOverlay.parent == null || isSettingsClosing) return
        isSettingsClosing = true
        settingsOverlay.animate().alpha(0f).setDuration(160).start()
        settingsDialog.animate().alpha(0f).scaleX(0.14f).scaleY(0.14f)
            .translationX(settingsStartTranslationX).translationY(settingsStartTranslationY)
            .setDuration(200).setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                if (settingsOverlay.parent != null) root.removeView(settingsOverlay)
                isSettingsClosing = false
                updateBrowserBackCallback()
            }.start()
    }

    private fun clearAllBrowsingData() {
        tabs.forEach {
            it.webView.clearHistory()
            it.webView.clearCache(true)
            it.webView.clearFormData()
        }
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        hideSettings()
        toast("浏览数据已清除")
    }

    private fun showAppInfoPage() {
        if (::settingsOverlay.isInitialized && settingsOverlay.parent != null) {
            root.removeView(settingsOverlay)
        }
        val palette = currentPalette()
        if (::appInfoOverlay.isInitialized && appInfoOverlay.parent != null) return
        appInfoOverlay = FrameLayout(this).apply {
            // 材质：应用信息遮罩 — 使用页面底色的实色遮罩营造景深
            setBackgroundColor(palette.page)
            alpha = 0f
            elevation = dp(50).toFloat()
            isClickable = true
        }
        appInfoCard = MaterialCardView(this).apply {
            radius = dp(28).toFloat()
            cardElevation = dp(18).toFloat()
            // 材质：应用信息卡 — 纯白厚毛玻璃
            setCardBackgroundColor(palette.card)
            strokeColor = palette.cardStroke
            strokeWidth = dp(1)
            alpha = 0f
            scaleX = 0.92f
            scaleY = 0.92f
            addView(ScrollView(this@MainActivity).apply {
                isFillViewport = true
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dp(28), dp(24), dp(28), dp(28))

                    // 头部：标题 + 关闭按钮
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(TextView(this@MainActivity).apply {
                            text = "应用信息"
                            textSize = 22f
                            setTextColor(palette.text)
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        addView(MaterialButton(this@MainActivity).apply {
                            text = "×"
                            textSize = 22f
                            contentDescription = "关闭"
                            minWidth = 0
                            minHeight = 0
                            insetTop = 0
                            insetBottom = 0
                            setPadding(0, 0, 0, 0)
                            cornerRadius = dp(15)
                            backgroundTintList = ColorStateList.valueOf(palette.group)
                            setTextColor(palette.icon)
                            setOnClickListener {
                                hideAppInfoPage()
                            }
                        }, LinearLayout.LayoutParams(dp(40), dp(40)))
                    })

                    // 放大的 App 图标
                    addView(TextView(this@MainActivity).apply {
                        text = "🌐"
                        textSize = 72f
                        gravity = Gravity.CENTER
                        setPadding(0, dp(32), 0, dp(16))
                    })

                    // 应用名称和版本号
                    addView(TextView(this@MainActivity).apply {
                        text = "浮悬浏览器"
                        textSize = 24f
                        gravity = Gravity.CENTER
                        setTextColor(palette.text)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = "版本 ${packageManager.getPackageInfo(packageName, 0).versionName}"
                        textSize = 15f
                        gravity = Gravity.CENTER
                        setTextColor(palette.mutedText)
                        setPadding(0, dp(6), 0, dp(28))
                    })

                    // GitHub 仓库链接
                    addView(MaterialButton(this@MainActivity).apply {
                        text = "GitHub 仓库"
                        textSize = 16f
                        isAllCaps = false
                        minWidth = 0
                        minHeight = 0
                        insetTop = 0
                        insetBottom = 0
                        cornerRadius = dp(16)
                        // 材质：纯灰白文本，无蓝调
                        setTextColor(if (isDarkPalette()) Color.rgb(235, 235, 235) else Color.WHITE)
                        backgroundTintList = ColorStateList.valueOf(palette.accent)
                        setPadding(dp(16), dp(0), dp(16), dp(0))
                        setOnClickListener {
                            openExternalUri(Uri.parse(GITHUB_REPO_URL))
                        }
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)).apply {
                        bottomMargin = dp(16)
                    })

                    addView(TextView(this@MainActivity).apply {
                        text = "Kotlin 原生 Android · WebView"
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setTextColor(palette.mutedText)
                    })
                })
            })
        }

        appInfoOverlay.addView(appInfoCard, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply { setMargins(dp(32), dp(80), dp(32), dp(80)) })

        root.addView(appInfoOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        appInfoOverlay.bringToFront()
        updateBrowserBackCallback()

        appInfoOverlay.animate().alpha(1f).setDuration(180).start()
        appInfoCard.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    private fun hideAppInfoPage(after: () -> Unit = {}) {
        if (!::appInfoOverlay.isInitialized || appInfoOverlay.parent == null) {
            after()
            return
        }
        appInfoOverlay.animate().alpha(0f).setDuration(150).withEndAction {
            if (appInfoOverlay.parent != null) root.removeView(appInfoOverlay)
            updateBrowserBackCallback()
            after()
        }.start()
    }

    private fun captureAnchor(view: View): PointF {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return PointF(location[0] + view.width / 2f, location[1] + view.height / 2f)
    }

    private fun openExternalUri(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            toast("设备未安装可处理此链接的应用")
        }
    }

    private fun roundedBackground(color: Int, radius: Int, strokeColor: Int = Color.TRANSPARENT): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (strokeColor != Color.TRANSPARENT) setStroke(dp(1), strokeColor)
        }
    }

    private fun isDarkPalette(): Boolean {
        return when (themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
    }

    private fun hostLabel(url: String): String {
        return runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }
            .getOrDefault("新标签页")
            .ifBlank { "新标签页" }
    }

    private fun trimTabTitle(title: String): String {
        return if (title.length > MAX_TAB_TITLE_LENGTH) title.take(MAX_TAB_TITLE_LENGTH) + "…" else title
    }

    private fun hideKeyboard() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(addressField.windowToken, 0)
        addressField.clearFocus()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroy() {
        frostedBackdropHandler.removeCallbacksAndMessages(null)
        if (!isChangingConfigurations) {
            tabs.forEach {
                it.webView.stopLoading()
                it.webView.destroy()
            }
        }
        super.onDestroy()
    }
}
