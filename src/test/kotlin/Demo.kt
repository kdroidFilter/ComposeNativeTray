
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.utils.SingleInstanceManager
import com.kdroid.composetray.utils.getTrayPosition
import com.kdroid.kmplog.Log
import com.kdroid.kmplog.d
import com.kdroid.kmplog.i
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main() = application {
    Log.setDevelopmentMode(true)
    val logTag = "NativeTray"

    Log.d("SavedTrayPosition", getTrayPosition().toString())

    var isWindowVisible by remember { mutableStateOf(true) }
    var textVisible by remember { mutableStateOf(false) }
    var alwaysShowTray by remember { mutableStateOf(false) }
    var hideOnClose by remember { mutableStateOf(true) }

    val isSingleInstance = SingleInstanceManager.isSingleInstance(onRestoreRequest = {
        isWindowVisible = true
    })

    if (!isSingleInstance) {
        exitApplication()
        return@application
    }

    // Tray Icon Paths
    val iconPath = Paths.get("src/test/resources/icon.png").toAbsolutePath().toString()
    val windowsIconPath = Paths.get("src/test/resources/icon.ico").toAbsolutePath().toString()

    // Updated condition for Tray visibility
    if (alwaysShowTray || !isWindowVisible) {
        Tray(
            iconPath = iconPath,
            windowsIconPath = windowsIconPath,
            primaryAction = {
                isWindowVisible = true
                Log.i(logTag, "On Primary Clicked")
            },
            primaryActionLinuxLabel = "Open the Application",
            tooltip = "My Application"
        ) {
            // Options SubMenu
            SubMenu(label = "Options") {
                Item(label = "Show Text") {
                    Log.i(logTag, "Show Text selected")
                    textVisible = true
                }
                Item(label = "Hide Text") {
                    Log.i(logTag, "Hide Text selected")
                    textVisible = false
                }
                SubMenu(label = "Advanced Sub-options") {
                    Item(label = "Advanced Option 1") {
                        Log.i(logTag, "Advanced Option 1 selected")
                    }
                    Item(label = "Advanced Option 2") {
                        Log.i(logTag, "Advanced Option 2 selected")
                    }
                }
            }

            Divider()

            // Tools SubMenu
            SubMenu(label = "Tools") {
                Item(label = "Calculator") {
                    Log.i(logTag, "Calculator launched")
                }
                Item(label = "Notepad") {
                    Log.i(logTag, "Notepad opened")
                }
            }

            Divider()

            // Checkable Items
            CheckableItem(label = "Enable notifications") { isChecked ->
                Log.i(logTag, "Notifications ${if (isChecked) "enabled" else "disabled"}")
            }

            Divider()

            Item(label = "About") {
                Log.i(logTag, "Application v1.0 - Developed by Elyahou")
            }

            Divider()

            CheckableItem(label = "Always show tray") { isChecked ->
                alwaysShowTray = isChecked
                Log.i(logTag, "Always show tray ${if (isChecked) "enabled" else "disabled"}")
            }

            CheckableItem(label = "Hide on close") { isChecked ->
                hideOnClose = isChecked
                Log.i(logTag, "Hide on close ${if (isChecked) "enabled" else "disabled"}")
            }

            Divider()

            Item(label = "Hide in tray") {
                isWindowVisible = false
            }

            Item(label = "Exit", isEnabled = true) {
                Log.i(logTag, "Exiting the application")
                dispose()
                exitApplication()
            }

            Item(label = "Version 1.0.0", isEnabled = false)
        }
    }

    Window(
        onCloseRequest = {
            if (hideOnClose) {
                isWindowVisible = false
            } else {
                exitApplication()
            }
        },
        title = "Compose Desktop Application with Two Screens",
        visible = isWindowVisible,
        icon = painterResource("icon.png") // Optional: Set window icon
    ) {
        App(textVisible, alwaysShowTray, hideOnClose) { alwaysShow, hideOnCloseState ->
            alwaysShowTray = alwaysShow
            hideOnClose = hideOnCloseState
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App(
    textVisible: Boolean,
    alwaysShowTray: Boolean,
    hideOnClose: Boolean,
    onToggleChange: (Boolean, Boolean) -> Unit
) {
    var currentScreen by remember { mutableStateOf(Screen.Screen1) }

    // Material3 Theme
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = MaterialTheme.colorScheme.primary,
            secondary = MaterialTheme.colorScheme.secondary,
            tertiary = MaterialTheme.colorScheme.tertiary
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Compose Tray App") },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            // Always Show Tray Toggle
                            Text("Always Show Tray")
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = alwaysShowTray,
                                onCheckedChange = { isChecked ->
                                    onToggleChange(isChecked, hideOnClose)
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            // Hide on Close Toggle
                            Text("Hide on Close")
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = hideOnClose,
                                onCheckedChange = { isChecked ->
                                    onToggleChange(alwaysShowTray, isChecked)
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            // Exit Button
                            Button(
                                onClick = { exitProcess(0) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Exit")
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        when (currentScreen) {
                            Screen.Screen1 -> ScreenOne(
                                onNavigate = { currentScreen = Screen.Screen2 },
                                textVisible = textVisible
                            )

                            Screen.Screen2 -> ScreenTwo(
                                onNavigate = { currentScreen = Screen.Screen1 },
                                textVisible = textVisible
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenOne(onNavigate: () -> Unit, textVisible: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "Welcome to Screen 1",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onNavigate) {
            Text("Go to Screen 2")
        }
        Spacer(modifier = Modifier.height(24.dp))
        if (textVisible) {
            Text(
                "This is the additional text displayed based on tray selection.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun ScreenTwo(onNavigate: () -> Unit, textVisible: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "Welcome to Screen 2",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onNavigate) {
            Text("Go back to Screen 1")
        }
        Spacer(modifier = Modifier.height(24.dp))
        if (textVisible) {
            Text(
                "This is the additional text displayed based on tray selection.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

enum class Screen {
    Screen1,
    Screen2
}
