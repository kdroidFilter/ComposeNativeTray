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
private var themeCallback: ThemeCallback? = nil

// Per-instance context keyed by the raw tray pointer (struct tray*)
private class TrayContext {
    let statusItem: NSStatusItem
    let clickHandler: InstanceButtonClickHandler
    var contextMenu: NSMenu?
    let appearanceObserver: MenuBarAppearanceObserver
    init(statusItem: NSStatusItem, clickHandler: InstanceButtonClickHandler, appearanceObserver: MenuBarAppearanceObserver) {
        self.statusItem = statusItem
        self.clickHandler = clickHandler
        self.appearanceObserver = appearanceObserver
    }
}

private var contexts: [UnsafeMutableRawPointer: TrayContext] = [:]

// MARK: - Menu delegate
private class MenuDelegate: NSObject, NSMenuDelegate {
    @objc func menuItemClicked(_ sender: NSMenuItem) {
        guard let menuItemPtr = sender.representedObject as? UnsafeMutableRawPointer else { return }
        let callbackPtr = menuItemPtr.advanced(by: 24)
            .assumingMemoryBound(to: MenuItemCallback?.self)
        callbackPtr.pointee?(menuItemPtr)
    }
}

// MARK: - Unified click handler per instance
@objc private class InstanceButtonClickHandler: NSObject {
    private let trayPtr: UnsafeMutableRawPointer
    init(trayPtr: UnsafeMutableRawPointer) { self.trayPtr = trayPtr }

    @objc func handleClick(_ sender: NSStatusBarButton) {
        guard let event = NSApp.currentEvent else { return }
        guard let ctx = contexts[trayPtr] else { return }

        if event.type == .rightMouseUp || event.modifierFlags.contains(.control) {
            if let menu = ctx.contextMenu {
                let menuLocation = NSPoint(
                    x: sender.frame.minX,
                    y: sender.frame.minY - 5
                )
                menu.popUp(positioning: nil, at: menuLocation, in: sender)
            }
        } else if event.type == .leftMouseUp {
            let callbackPtr = trayPtr.advanced(by: 24)
                .assumingMemoryBound(to: TrayCallback?.self)
            callbackPtr.pointee?(trayPtr)
        }
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
    if !Thread.isMainThread {
        return DispatchQueue.main.sync { tray_init(tray) }
    }

    loopStatus = 0

    if app == nil { app = NSApplication.shared }
    if statusBar == nil { statusBar = NSStatusBar.system }
    if menuDelegate == nil { menuDelegate = MenuDelegate() }

    // Create a new status item for this tray instance
    guard let bar = statusBar else { return -1 }
    let statusItem = bar.statusItem(withLength: NSStatusItem.variableLength)

    let observer = MenuBarAppearanceObserver()
    observer.startObserving(statusItem)

    let clickHandler = InstanceButtonClickHandler(trayPtr: tray)

    let ctx = TrayContext(statusItem: statusItem, clickHandler: clickHandler, appearanceObserver: observer)
    contexts[tray] = ctx

    // First-time update sets image/tooltip/menu and target/action
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

    guard let ctx = contexts[tray] else { return }
    let statusItem = ctx.statusItem

    let iconPathPtr  = tray.load(as: UnsafePointer<CChar>?.self)
    let tooltipPtr   = tray.advanced(by: 8).load(as: UnsafePointer<CChar>?.self)
    let menuPtr      = tray.advanced(by: 16).load(as: UnsafeMutableRawPointer?.self)
    let callbackPtr  = tray.advanced(by: 24).load(as: TrayCallback?.self)

    // Update icon
    if let iconPath = iconPathPtr.flatMap({ String(cString: $0) }),
       let image = NSImage(contentsOfFile: iconPath) {
        let height = NSStatusBar.system.thickness
        let width  = image.size.width * (height / image.size.height)
        image.size = NSSize(width: width, height: height)
        statusItem.button?.image = image
    }

    // Update tooltip
    statusItem.button?.toolTip = tooltipPtr.flatMap { String(cString: $0) }

    // Menu and actions configuration
    // Important: never set statusItem.menu to keep highlight control
    statusItem.menu = nil

    if let menuPtr = menuPtr {
        // Create and store the menu without assigning it to statusItem
        ctx.contextMenu = nativeMenu(from: menuPtr)
    } else {
        ctx.contextMenu = nil
    }

    // Configure button actions
    if callbackPtr != nil || ctx.contextMenu != nil {
        // We have either a callback, a menu, or both
        statusItem.button?.target = ctx.clickHandler
        statusItem.button?.action = #selector(InstanceButtonClickHandler.handleClick(_:))
        statusItem.button?.sendAction(on: NSEvent.EventTypeMask.leftMouseUp.union(.rightMouseUp))
    } else {
        // Neither callback nor menu
        statusItem.button?.target = nil
        statusItem.button?.action = nil
    }
}

@_cdecl("tray_dispose")
public func tray_dispose(_ tray: UnsafeMutableRawPointer) {
    if !Thread.isMainThread {
        return DispatchQueue.main.async { tray_dispose(tray) }
    }
    if let ctx = contexts.removeValue(forKey: tray) {
        ctx.appearanceObserver.invalidate()
        NSStatusBar.system.removeStatusItem(ctx.statusItem)
        if trayInstance == tray { trayInstance = nil }
    }
}

@_cdecl("tray_exit")
public func tray_exit() {
    if !Thread.isMainThread {
        return DispatchQueue.main.async { tray_exit() }
    }

    loopStatus = -1

    // Dispose all existing status items
    for (ptr, ctx) in contexts {
        ctx.appearanceObserver.invalidate()
        NSStatusBar.system.removeStatusItem(ctx.statusItem)
        if trayInstance == ptr { trayInstance = nil }
    }
    contexts.removeAll()

    // Optionally release delegates
    menuDelegate = nil
}

@_cdecl("tray_set_theme_callback")
public func tray_set_theme_callback(_ cb: @escaping ThemeCallback) {
    themeCallback = cb
}

@_cdecl("tray_is_menu_dark")
public func tray_is_menu_dark() -> Int32 {
    if !Thread.isMainThread {
        return DispatchQueue.main.sync { tray_is_menu_dark() }
    }
    let ctx = trayInstance.flatMap { contexts[$0] } ?? contexts.values.first
    let appearance: NSAppearance
    if let button = ctx?.statusItem.button {
        appearance = button.effectiveAppearance
    } else {
        appearance = NSApp.effectiveAppearance
    }
    guard let name = appearance.bestMatch(from: [.darkAqua, .aqua]) else {
        return 1
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
    let ctx = trayInstance.flatMap { contexts[$0] } ?? contexts.values.first
    guard
        let button = ctx?.statusItem.button,
        let window = button.window,
        let screen = window.screen
    else {
        x?.pointee = 0
        y?.pointee = 0
        return 0        // unreliable coordinates
    }

    // Button frame in screen space (origin at bottom-left)
    var rect = button.convert(button.bounds, to: nil as NSView?)
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
    let ctx = trayInstance.flatMap { contexts[$0] } ?? contexts.values.first
    guard let button = ctx?.statusItem.button,
          let screen = button.window?.screen else {
        return strdup("top-right")          // default value
    }

    let rect   = button.window!.convertToScreen(
        button.convert(button.bounds, to: nil as NSView?)
    )
    let midX   = screen.frame.midX
    let region = rect.minX < midX ? "top-left" : "top-right"
    return strdup(region)                   // to be freed on JVM/JNA side
}

// Per-instance geometry exports
@_cdecl("tray_get_status_item_position_for")
public func tray_get_status_item_position_for(
    _ tray: UnsafeMutableRawPointer?,
    _ x: UnsafeMutablePointer<Int32>?,
    _ y: UnsafeMutablePointer<Int32>?
) -> Int32 {
    guard let tray = tray, let ctx = contexts[tray] else {
        x?.pointee = 0
        y?.pointee = 0
        return 0
    }
    guard
        let button = ctx.statusItem.button,
        let window = button.window,
        let screen = window.screen
    else {
        x?.pointee = 0
        y?.pointee = 0
        return 0
    }

    var rect = button.convert(button.bounds, to: nil as NSView?)
    rect      = window.convertToScreen(rect)

    x?.pointee = Int32(lround(rect.midX))
    let flippedY = Int32(screen.frame.maxY - rect.maxY)
    y?.pointee = flippedY
    return 1
}

@_cdecl("tray_get_status_item_region_for")
public func tray_get_status_item_region_for(
    _ tray: UnsafeMutableRawPointer?
) -> UnsafeMutablePointer<CChar>? {
    guard let tray = tray, let ctx = contexts[tray] else {
        return strdup("top-right")
    }
    guard let button = ctx.statusItem.button,
          let screen = button.window?.screen else {
        return strdup("top-right")
    }
    let rect   = button.window!.convertToScreen(
        button.convert(button.bounds, to: nil as NSView?)
    )
    let midX   = screen.frame.midX
    let region = rect.minX < midX ? "top-left" : "top-right"
    return strdup(region)
}