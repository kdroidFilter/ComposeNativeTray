import Cocoa
import Foundation

// Types for C callbacks
public typealias TrayCallback = @convention(c) (UnsafeMutableRawPointer?) -> Void
public typealias MenuItemCallback = @convention(c) (UnsafeMutableRawPointer?) -> Void
public typealias ThemeCallback = @convention(c) (Int32) -> Void

// MARK: - Static globals (kept for C interop)
private var loopStatus: Int32 = 0
private var trayInstance: UnsafeMutableRawPointer? = nil
private var app: NSApplication? = nil
private var statusBar: NSStatusBar? = nil
private var statusItem: NSStatusItem? = nil
private var themeCallback: ThemeCallback? = nil

// MARK: - Menu delegate
private class MenuDelegate: NSObject, NSMenuDelegate {
    @objc func menuItemClicked(_ sender: NSMenuItem) {
        guard let menuItemPtr = sender.representedObject as? UnsafeMutableRawPointer else { return }
        let callbackPtr = menuItemPtr.advanced(by: 16)
            .assumingMemoryBound(to: MenuItemCallback?.self)
        callbackPtr.pointee?(menuItemPtr)
    }

    func menuWillOpen(_ menu: NSMenu) {
        guard menu == statusItem?.menu,
              let currentEvent = NSApp.currentEvent,
              currentEvent.buttonNumber == 0,
              let trayPtr = trayInstance else { return }

        // Left‑click: cancel menu & fire callback immediately
        menu.cancelTracking()
        let callbackPtr = trayPtr.advanced(by: 24)
            .assumingMemoryBound(to: TrayCallback?.self)
        callbackPtr.pointee?(trayPtr)
    }
}

// MARK: - Left‑click handler when no menu is present
@objc private class ButtonClickHandler: NSObject {
    @objc func handleClick(_ sender: NSStatusBarButton) {
        guard let trayPtr = trayInstance else { return }
        let callbackPtr = trayPtr.advanced(by: 24)
            .assumingMemoryBound(to: TrayCallback?.self)
        callbackPtr.pointee?(trayPtr)
    }
}

// MARK: - Appearance observer with ultra‑low latency
/// Detects menu‑bar theme changes in <60 ms using KVO + GCD debouncing.
private class MenuBarAppearanceObserver {
    private var observation: NSKeyValueObservation?
    private var workItem: DispatchWorkItem?
    private var lastAppearance: NSAppearance.Name?

    /// Debounce delay before first evaluation (keep tiny but non‑zero).
    private let debounce: TimeInterval = 0.04   // 40 ms
    /// Settling delay to avoid reporting intermediate states.
    private let settle: TimeInterval  = 0.005   // 5 ms

    func startObserving(_ statusItem: NSStatusItem) {
        observation = statusItem.button?.observe(
            \.effectiveAppearance,
            options: [.initial, .new]
        ) { [weak self] button, _ in
            self?.scheduleCheck(for: button.effectiveAppearance)
        }
    }

    private func scheduleCheck(for appearance: NSAppearance) {
        workItem?.cancel()

        let item = DispatchWorkItem { [weak self] in
            self?.evaluate(appearance)
        }
        workItem = item
        DispatchQueue.main.asyncAfter(deadline: .now() + debounce, execute: item)
    }

    private func evaluate(_ appearance: NSAppearance) {
        guard let matched = appearance.bestMatch(from: [.darkAqua, .aqua]),
              matched != lastAppearance else { return }
        lastAppearance = matched

        // Allow the system a single run‑loop to settle, then notify.
        DispatchQueue.main.asyncAfter(deadline: .now() + settle) {
            themeCallback?(matched == .darkAqua ? 1 : 0)
        }
    }

    func invalidate() {
        observation?.invalidate()
        observation = nil
        workItem?.cancel()
    }
}

// MARK: - Globals that need to live for app lifetime
private var menuDelegate: MenuDelegate?
private var buttonClickHandler: ButtonClickHandler?
private var appearanceObserver: MenuBarAppearanceObserver?

// MARK: - Helpers
private func nativeMenu(from menuPtr: UnsafeMutableRawPointer) -> NSMenu {
    let menu = NSMenu()
    menu.autoenablesItems = false
    menu.delegate = menuDelegate

    var currentPtr = menuPtr
    while true {
        guard let textPtr = currentPtr.load(as: UnsafePointer<CChar>?.self) else { break }
        let title = String(cString: textPtr)

        if title == "-" {
            menu.addItem(NSMenuItem.separator())
        } else {
            // Charger les champs avec les nouveaux offsets
            let iconPathPtr = currentPtr.advanced(by: 8).load(as: UnsafePointer<CChar>?.self)
            let disabled = currentPtr.advanced(by: 16).load(as: Int32.self) == 1
            let checked  = currentPtr.advanced(by: 20).load(as: Int32.self) == 1
            let callback = currentPtr.advanced(by: 24).load(as: MenuItemCallback?.self)
            let submenu  = currentPtr.advanced(by: 32).load(as: UnsafeMutableRawPointer?.self)

            let item = NSMenuItem(title: title, action: nil, keyEquivalent: "")
            item.isEnabled = !disabled
            item.state = checked ? .on : .off
            item.representedObject = currentPtr

            // Ajouter l'icône si disponible
            if let iconPath = iconPathPtr.flatMap({ String(cString: $0) }),
               let image = NSImage(contentsOfFile: iconPath) {
                // Redimensionner l'icône à une taille appropriée pour le menu
                let menuIconSize = NSSize(width: 16, height: 16)
                image.size = menuIconSize
                item.image = image
            }

            if callback != nil {
                item.target = menuDelegate
                item.action = #selector(MenuDelegate.menuItemClicked(_:))
            }
            menu.addItem(item)

            if let submenuPtr = submenu {
                menu.setSubmenu(nativeMenu(from: submenuPtr), for: item)
            }
        }

        currentPtr = currentPtr.advanced(by: 40)  // Nouveau offset avec le champ icon_filepath
    }
    return menu
}

// MARK: - C shim
@_cdecl("tray_get_instance")
public func tray_get_instance() -> UnsafeMutableRawPointer? { trayInstance }

@_cdecl("tray_init")
public func tray_init(_ tray: UnsafeMutableRawPointer) -> Int32 {
    // Guarantee work is on main thread.
    if !Thread.isMainThread {
        return DispatchQueue.main.sync { tray_init(tray) }
    }

    loopStatus = 0
    menuDelegate = MenuDelegate()
    buttonClickHandler = ButtonClickHandler()
    appearanceObserver = MenuBarAppearanceObserver()

    app = NSApplication.shared
    statusBar = NSStatusBar.system
    statusItem = statusBar?.statusItem(withLength: NSStatusItem.variableLength)

    if let statusItem = statusItem {
        appearanceObserver?.startObserving(statusItem)
    }

    tray_update(tray)
    return 0
}

@_cdecl("tray_loop")
public func tray_loop(_ blocking: Int32) -> Int32 {
    if !Thread.isMainThread {
        return DispatchQueue.main.sync { tray_loop(blocking) }
    }

    let until = blocking != 0 ? Date.distantFuture : Date.distantPast
    if let event = app?.nextEvent(matching: .any, until: until, inMode: .default, dequeue: true) {
        app?.sendEvent(event)
    }
    return loopStatus
}

@_cdecl("tray_update")
public func tray_update(_ tray: UnsafeMutableRawPointer) {
    if !Thread.isMainThread {
        return DispatchQueue.main.async { tray_update(tray) }
    }

    trayInstance = tray

    let iconPathPtr  = tray.load(as: UnsafePointer<CChar>?.self)
    let tooltipPtr   = tray.advanced(by: 8).load(as: UnsafePointer<CChar>?.self)
    let menuPtr      = tray.advanced(by: 16).load(as: UnsafeMutableRawPointer?.self)
    let callbackPtr  = tray.advanced(by: 24).load(as: TrayCallback?.self)

    if let iconPath = iconPathPtr.flatMap({ String(cString: $0) }),
       let image = NSImage(contentsOfFile: iconPath) {
        let height = NSStatusBar.system.thickness
        let width  = image.size.width * (height / image.size.height)
        image.size = NSSize(width: width, height: height)
        statusItem?.button?.image = image
    }

    statusItem?.button?.toolTip = tooltipPtr.flatMap { String(cString: $0) }

    if let menuPtr = menuPtr {
        statusItem?.menu = nativeMenu(from: menuPtr)
        statusItem?.button?.target = nil
        statusItem?.button?.action = nil
    } else if callbackPtr != nil {
        statusItem?.menu = nil
        statusItem?.button?.target = buttonClickHandler
        statusItem?.button?.action = #selector(ButtonClickHandler.handleClick(_:))
        statusItem?.button?.sendAction(on: [.leftMouseUp])
    } else {
        statusItem?.menu = nil
        statusItem?.button?.target = nil
        statusItem?.button?.action = nil
    }
}

@_cdecl("tray_exit")
public func tray_exit() {
    if !Thread.isMainThread {
        return DispatchQueue.main.async { tray_exit() }
    }

    loopStatus = -1
    appearanceObserver?.invalidate()

    if let statusItem = statusItem {
        NSStatusBar.system.removeStatusItem(statusItem)
    }

    trayInstance = nil
    statusItem = nil
    menuDelegate = nil
    buttonClickHandler = nil
    appearanceObserver = nil
}

@_cdecl("tray_set_theme_callback")
public func tray_set_theme_callback(_ cb: @escaping ThemeCallback) {
    themeCallback = cb
}

@_cdecl("tray_is_menu_dark")
public func tray_is_menu_dark() -> Int32 {
    guard let name = statusItem?.button?.effectiveAppearance.bestMatch(from: [.darkAqua, .aqua]) else {
        return 1 // assume dark if unknown
    }
    return name == .darkAqua ? 1 : 0
}

// MARK: - Status‑item geometry (exported to C)

// Returns 1 if the coordinate is precise, 0 if we had to use a fallback.
@_cdecl("tray_get_status_item_position")
public func tray_get_status_item_position(
    _ x: UnsafeMutablePointer<Int32>?,
    _ y: UnsafeMutablePointer<Int32>?
) -> Int32
{
    guard
        let button = statusItem?.button,
        let window = button.window,
        let screen = window.screen
    else {
        x?.pointee = 0
        y?.pointee = 0
        return 0        // unreliable coordinates
    }

    // Button frame in screen space (origin at bottom-left)
    var rect = button.convert(button.bounds, to: nil)
    rect      = window.convertToScreen(rect)

    // -- X ---------------------------------------------------------------
    // Horizontal center of the icon (ideal for centered placement)
    x?.pointee = Int32(lround(rect.midX))

    // -- Y ---------------------------------------------------------------
    // Inverted coordinate system to match Windows/Linux (origin at top)
    let flippedY = Int32(screen.frame.maxY - rect.maxY)
    y?.pointee = flippedY

    return 1            // precise coordinates
}

/// Returns "top-left" or "top-right" (menu-bar always at top).
@_cdecl("tray_get_status_item_region")
public func tray_get_status_item_region() -> UnsafeMutablePointer<CChar>? {
    guard let button = statusItem?.button,
          let screen = button.window?.screen else {
        return strdup("top-right")          // default value
    }

    let rect   = button.window!.convertToScreen(
        button.convert(button.bounds, to: nil)
    )
    let midX   = screen.frame.midX
    let region = rect.minX < midX ? "top-left" : "top-right"
    return strdup(region)                   // to be freed on JVM/JNA side
}