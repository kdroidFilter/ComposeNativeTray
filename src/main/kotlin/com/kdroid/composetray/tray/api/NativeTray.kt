package com.kdroid.composetray.tray.api

import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.tray.impl.AwtTrayInitializer
import com.kdroid.composetray.tray.impl.LinuxTrayInitializer
import com.kdroid.composetray.tray.impl.WindowsTrayInitializer
import com.kdroid.composetray.utils.OperatingSystem
import com.kdroid.composetray.utils.PlatformUtils

class NativeTray(
    iconPath: String,
    windowsIconPath: String = iconPath,
    tooltip: String = "",
    menuContent: TrayMenuBuilder.() -> Unit
) {

    init {
        when (PlatformUtils.currentOS) {
            OperatingSystem.LINUX -> LinuxTrayInitializer.initialize(iconPath, menuContent)
            OperatingSystem.WINDOWS -> WindowsTrayInitializer.initialize(windowsIconPath, tooltip, menuContent)
            OperatingSystem.MAC, OperatingSystem.UNKNOWN -> AwtTrayInitializer.initialize(iconPath, tooltip, menuContent)
        }
    }

}
