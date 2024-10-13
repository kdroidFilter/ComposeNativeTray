package com.kdroid.composetray.tray.api

import com.kdroid.composetray.menu.TrayMenu
import com.kdroid.composetray.tray.impl.AwtTrayInitializer
import com.kdroid.composetray.tray.impl.LinuxTrayInitializer
import com.kdroid.composetray.tray.impl.SwingTrayInitializer
import com.kdroid.composetray.tray.impl.WindowsTrayInitializer
import com.kdroid.composetray.utils.OperatingSystem
import com.kdroid.composetray.utils.PlatformUtils

class NativeTray(
    iconPath: String,
    tooltip: String = "",
    menuContent: TrayMenu.() -> Unit
) {

    init {
        when (PlatformUtils.currentOS) {
            OperatingSystem.LINUX -> LinuxTrayInitializer.initialize(iconPath, menuContent)
            OperatingSystem.WINDOWS -> WindowsTrayInitializer.initialize(iconPath, tooltip, menuContent)
            OperatingSystem.MAC -> AwtTrayInitializer.initialize(iconPath, tooltip, menuContent)
            OperatingSystem.UNKNOWN -> SwingTrayInitializer.initialize(iconPath, tooltip, menuContent)
        }
    }

}