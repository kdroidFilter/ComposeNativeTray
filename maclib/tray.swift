// Modified tray.swift (reduce debounce to 0.1s and asyncAfter to 0.01s for faster detection)
import Cocoa
import Foundation

// Types for C callbacks
public typealias TrayCallback = @convention(c) (UnsafeMutableRawPointer?) -> Void
public typealias MenuItemCallback = @convention(c) (UnsafeMutableRawPointer?) -> Void
public typealias ThemeCallback = @convention(c) (Int32) -> Void

// Static variables
private var loopStatus: Int32 = 0
private var trayInstance: UnsafeMutableRawPointer? = nil
private var app: NSApplication? = nil
private var statusBar: NSStatusBar? = nil
private var statusItem: NSStatusItem? = nil
private var themeCallback: ThemeCallback? = nil

// Delegate for the menu
class MenuDelegate: NSObject, NSMenuDelegate {
    @objc func menuItemClicked(_ sender: NSMenuItem) {
        // Get the pointer to the menu item structure
        guard let menuItemPtr = sender.representedObject as? UnsafeMutableRawPointer else { return }

        // Read the callback from the structure (offset 16)
        let callbackPtr = menuItemPtr.advanced(by: 16).assumingMemoryBound(to: MenuItemCallback?.self)
        if let callback = callbackPtr.pointee {
            callback(menuItemPtr)
        }
    }

    func menuWillOpen(_ menu: NSMenu) {
        if menu == statusItem?.menu, let currentEvent = NSApp.currentEvent, currentEvent.buttonNumber == 0 {
            // For a left click, cancel the menu display and call the callback
            menu.cancelTracking()

            if let trayPtr = trayInstance {
                // Read the callback from the tray structure (offset 24 bytes)
                let callbackPtr = trayPtr.advanced(by: 24).assumingMemoryBound(to: TrayCallback?.self)
                if let callback = callbackPtr.pointee {
                    callback(trayPtr)
                }
            }
        }
    }
}

// Button click handler
@objc class ButtonClickHandler: NSObject {
    @objc func handleClick(_ sender: NSStatusBarButton) {
        if let trayPtr = trayInstance {
            // Read the callback from the tray structure (offset 24 bytes)
            let callbackPtr = trayPtr.advanced(by: 24).assumingMemoryBound(to: TrayCallback?.self)
            if let callback = callbackPtr.pointee {
                callback(trayPtr)
            }
        }
    }
}

// Appearance observer
class MenuBarAppearanceObserver: NSObject {
    var debounceTimer: Timer?
    var lastAppearanceName: NSAppearance.Name?

    override func observeValue(forKeyPath keyPath: String?, of object: Any?, change: [NSKeyValueChangeKey : Any]?, context: UnsafeMutableRawPointer?) {
        if keyPath == "button.effectiveAppearance" {
            debounceTimer?.invalidate()
            debounceTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: false) { [weak self] _ in
                guard let self = self,
                      let statusItem = object as? NSStatusItem,
                      let appearance = statusItem.button?.effectiveAppearance else { return }
                self.updateForAppearance(appearance)
            }
        }
    }

    func updateForAppearance(_ appearance: NSAppearance) {
        let name = appearance.bestMatch(from: [.darkAqua, .aqua])
        guard name != lastAppearanceName else { return }

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.01) { [weak self] in
            guard let self = self,
                  let currentAppearance = statusItem?.button?.effectiveAppearance else { return }
            let currentName = currentAppearance.bestMatch(from: [.darkAqua, .aqua])
            guard currentName == name && currentName != self.lastAppearanceName else { return }
            self.lastAppearanceName = currentName
            let isDark = currentName == .darkAqua ? Int32(1) : Int32(0)
            if let cb = themeCallback {
                cb(isDark)
            }
        }
    }
}

// Delegate instances
private var menuDelegate: MenuDelegate? = nil
private var buttonClickHandler: ButtonClickHandler? = nil
private var appearanceObserver: MenuBarAppearanceObserver? = nil

// Function to create a native menu from a C structure
private func nativeMenu(from menuPtr: UnsafeMutableRawPointer) -> NSMenu {
    let menu = NSMenu()
    menu.autoenablesItems = false
    menu.delegate = menuDelegate

    var currentPtr = menuPtr

    while true {
        // Read the pointer to the text
        let textPtr = currentPtr.load(as: UnsafePointer<CChar>?.self)

        // If text is nil, we've reached the end
        guard let text = textPtr else { break }

        let textString = String(cString: text)

        if textString == "-" {
            menu.addItem(NSMenuItem.separator())
        } else {
            // Read the other fields
            let disabled = currentPtr.advanced(by: 8).load(as: Int32.self)
            let checked = currentPtr.advanced(by: 12).load(as: Int32.self)
            let callbackPtr = currentPtr.advanced(by: 16).load(as: MenuItemCallback?.self)
            let submenuPtr = currentPtr.advanced(by: 24).load(as: UnsafeMutableRawPointer?.self)

            let menuItem = NSMenuItem(title: textString, action: nil, keyEquivalent: "")
            menuItem.isEnabled = disabled == 0
            menuItem.state = checked == 1 ? .on : .off
            menuItem.representedObject = currentPtr

            // If the item has a callback, set the action
            if callbackPtr != nil {
                menuItem.target = menuDelegate
                menuItem.action = #selector(MenuDelegate.menuItemClicked(_:))
            }

            menu.addItem(menuItem)

            // Handle submenus
            if let submenuPtr = submenuPtr {
                menu.setSubmenu(nativeMenu(from: submenuPtr), for: menuItem)
            }
        }

        // Move to the next element (32 bytes)
        currentPtr = currentPtr.advanced(by: 32)
    }

    return menu
}

// C API functions for JNA
@_cdecl("tray_get_instance")
public func tray_get_instance() -> UnsafeMutableRawPointer? {
    return trayInstance
}

@_cdecl("tray_init")
public func tray_init(_ tray: UnsafeMutableRawPointer) -> Int32 {
    // Execute on the main thread
    if !Thread.isMainThread {
        var result: Int32 = -1
        DispatchQueue.main.sync {
            result = tray_init(tray)
        }
        return result
    }

    // Reset loopStatus to allow reuse
    loopStatus = 0

    menuDelegate = MenuDelegate()
    buttonClickHandler = ButtonClickHandler()
    appearanceObserver = MenuBarAppearanceObserver()
    app = NSApplication.shared
    statusBar = NSStatusBar.system
    statusItem = statusBar?.statusItem(withLength: NSStatusItem.variableLength)
    statusItem?.addObserver(appearanceObserver!, forKeyPath: "button.effectiveAppearance", options: [.initial, .new], context: nil)

    tray_update(tray)

    return 0
}

@_cdecl("tray_loop")
public func tray_loop(_ blocking: Int32) -> Int32 {
    // Execute on the main thread
    if !Thread.isMainThread {
        var result: Int32 = -1
        DispatchQueue.main.sync {
            result = tray_loop(blocking)
        }
        return result
    }

    let until = blocking != 0 ? Date.distantFuture : Date.distantPast

    if let event = app?.nextEvent(matching: .any, until: until, inMode: .default, dequeue: true) {
        app?.sendEvent(event)
    }

    return loopStatus
}

@_cdecl("tray_update")
public func tray_update(_ tray: UnsafeMutableRawPointer) {
    // Execute on the main thread
    if !Thread.isMainThread {
        DispatchQueue.main.async {
            tray_update(tray)
        }
        return
    }

    trayInstance = tray

    // Read the fields from the tray structure
    let iconPathPtr = tray.load(as: UnsafePointer<CChar>?.self)
    let tooltipPtr = tray.advanced(by: 8).load(as: UnsafePointer<CChar>?.self)
    let menuPtr = tray.advanced(by: 16).load(as: UnsafeMutableRawPointer?.self)
    let callbackPtr = tray.advanced(by: 24).load(as: TrayCallback?.self)

    // Update the icon
    if let iconPath = iconPathPtr {
        let iconString = String(cString: iconPath)
        let iconHeight = NSStatusBar.system.thickness

        if let image = NSImage(contentsOfFile: iconString) {
            let width = image.size.width * (iconHeight / image.size.height)
            image.size = NSSize(width: width, height: iconHeight)
            statusItem?.button?.image = image
        }
    }

    // Update the tooltip
    if let tooltip = tooltipPtr {
        statusItem?.button?.toolTip = String(cString: tooltip)
    }

    // Update the menu or handle click
    if let menuPtr = menuPtr {
        statusItem?.menu = nativeMenu(from: menuPtr)
        // Remove any existing click handler when there's a menu
        statusItem?.button?.target = nil
        statusItem?.button?.action = nil
    } else if callbackPtr != nil {
        // No menu, but there's a callback - handle direct click
        statusItem?.menu = nil
        statusItem?.button?.target = buttonClickHandler
        statusItem?.button?.action = #selector(ButtonClickHandler.handleClick(_:))
        statusItem?.button?.sendAction(on: [.leftMouseUp])
    } else {
        // No menu and no callback
        statusItem?.menu = nil
        statusItem?.button?.target = nil
        statusItem?.button?.action = nil
    }
}

@_cdecl("tray_exit")
public func tray_exit() {
    // Execute on the main thread
    if !Thread.isMainThread {
        DispatchQueue.main.async {
            tray_exit()
        }
        return
    }

    loopStatus = -1

    // Remove the observer
    if let observer = appearanceObserver {
        statusItem?.removeObserver(observer, forKeyPath: "button.effectiveAppearance")
    }
    appearanceObserver = nil

    // Remove the status bar item
    if let statusItem = statusItem {
        NSStatusBar.system.removeStatusItem(statusItem)
    }

    // Reset the variables
    trayInstance = nil
    statusItem = nil
    menuDelegate = nil
    buttonClickHandler = nil
}

@_cdecl("tray_set_theme_callback")
public func tray_set_theme_callback(_ cb: @escaping ThemeCallback) {
    themeCallback = cb
}

@_cdecl("tray_is_menu_dark")
public func tray_is_menu_dark() -> Int32 {
    guard let button = statusItem?.button else {
        return 1 // Default to true (dark) if not available
    }
    let appearance = button.effectiveAppearance
    let name = appearance.bestMatch(from: [.darkAqua, .aqua])
    return name == .darkAqua ? 1 : 0
}