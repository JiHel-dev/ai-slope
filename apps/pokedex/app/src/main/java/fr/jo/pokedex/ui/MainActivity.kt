package fr.jo.pokedex.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import fr.jo.pokedex.PokedexApp
import fr.jo.pokedex.R
import fr.jo.pokedex.kiosk.PinHasher
import fr.jo.pokedex.ui.theme.PokedexTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private enum class ParentalStep { HIDDEN, PIN, PANEL, NEW_PIN, CONFIRM_PIN }

    private val container by lazy { (application as PokedexApp).container }
    private val viewModel: PokedexViewModel by viewModels { PokedexViewModel.factory(container) }

    private val micGranted = mutableStateOf(false)
    private val statusRefresh = mutableIntStateOf(0)
    private var parentalVisible = false

    private val requestMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        micGranted.value = it
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        onBackPressedDispatcher.addCallback(this) { /* The back key never leaves the Pokédex. */ }

        container.kiosk.applyPolicies()
        micGranted.value = hasMicPermission()
        lifecycleScope.launch {
            if (!hasMicPermission() && !container.settings.micPermissionAsked.first()) {
                container.settings.setMicPermissionAsked()
                requestMic.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        setContent { PokedexTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        micGranted.value = hasMicPermission()
        hideSystemBars()
        if (!parentalVisible) container.kiosk.endMaintenance(this)
        container.kiosk.enterIfNeeded(this)
        statusRefresh.intValue++
    }

    override fun onPause() {
        super.onPause()
        viewModel.onInterrupted()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Composable
    private fun Root() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val data by viewModel.data.collectAsState()
        val mode by viewModel.mode.collectAsState()
        val maintenance by container.kiosk.maintenance.collectAsState()
        val isDefaultPin by container.settings.isDefaultPin.collectAsState(initial = false)
        val lifecycleState by lifecycle.currentStateFlow.collectAsState()
        var step by rememberSaveable { mutableStateOf(ParentalStep.HIDDEN) }
        var pendingPin by remember { mutableStateOf("") }

        SideEffect { parentalVisible = step != ParentalStep.HIDDEN }

        val wrongPin = stringResource(R.string.pin_wrong)
        val invalidPin = stringResource(R.string.pin_invalid)
        val mismatch = stringResource(R.string.pin_mismatch)

        Box(Modifier.fillMaxSize()) {
            HomeScreen(
                state = state,
                onListen = { viewModel.onListenPressed(micGranted.value) },
                onReplay = viewModel::onReplayPressed,
            )

            when (step) {
                ParentalStep.HIDDEN -> ParentalHotspots {
                    viewModel.onInterrupted()
                    step = ParentalStep.PIN
                }

                ParentalStep.PIN -> key(step) {
                    PinPad(
                        title = stringResource(R.string.pin_title),
                        onSubmit = { pin ->
                            if (container.settings.checkPin(pin)) {
                                step = ParentalStep.PANEL
                                null
                            } else {
                                wrongPin
                            }
                        },
                        onCancel = { step = ParentalStep.HIDDEN },
                    )
                }

                ParentalStep.PANEL -> {
                    statusRefresh.intValue
                    ParentalScreen(
                        status = ParentalStatus(
                            data = data,
                            uiState = state,
                            recognitionMode = mode,
                            kioskMode = container.kiosk.mode,
                            kioskLocked = container.kiosk.isLocked,
                            maintenance = maintenance,
                            micGranted = micGranted.value,
                            isDefaultPin = isDefaultPin,
                        ),
                        actions = ParentalActions(
                            onRequestMic = {
                                container.kiosk.startMaintenance(this@MainActivity)
                                requestMic.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onOpenAppSettings = {
                                openSystemScreen(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                                )
                            },
                            onModeChange = viewModel::setRecognitionMode,
                            onStartMaintenance = {
                                container.kiosk.startMaintenance(this@MainActivity)
                                statusRefresh.intValue++
                            },
                            onOpenSettings = { openSystemScreen(Intent(Settings.ACTION_SETTINGS)) },
                            onChangePin = { step = ParentalStep.NEW_PIN },
                            onReload = { viewModel.reload() },
                            onRemoveOwner = {
                                container.kiosk.removeDeviceOwner(this@MainActivity)
                                statusRefresh.intValue++
                            },
                            onClose = {
                                step = ParentalStep.HIDDEN
                                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                    container.kiosk.endMaintenance(this@MainActivity)
                                    statusRefresh.intValue++
                                }
                            },
                        ),
                        resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED),
                    )
                }

                ParentalStep.NEW_PIN -> key(step) {
                    PinPad(
                        title = stringResource(R.string.pin_new_title),
                        onSubmit = { pin ->
                            if (PinHasher.isValidPin(pin)) {
                                pendingPin = pin
                                step = ParentalStep.CONFIRM_PIN
                                null
                            } else {
                                invalidPin
                            }
                        },
                        onCancel = { step = ParentalStep.PANEL },
                    )
                }

                ParentalStep.CONFIRM_PIN -> key(step) {
                    PinPad(
                        title = stringResource(R.string.pin_confirm_title),
                        onSubmit = { pin ->
                            if (pin == pendingPin) {
                                container.settings.setPin(pin)
                                pendingPin = ""
                                step = ParentalStep.PANEL
                                null
                            } else {
                                mismatch
                            }
                        },
                        onCancel = { step = ParentalStep.PANEL },
                    )
                }
            }
        }
    }

    private fun openSystemScreen(intent: Intent) {
        container.kiosk.startMaintenance(this)
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
