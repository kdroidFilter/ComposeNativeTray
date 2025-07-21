//
// Created by Elie Gambache on 14/07/2025.
//

import Cocoa

class AppDelegate: NSObject, NSApplicationDelegate {
    var statusItem: NSStatusItem?
    private var lastAppearanceName: NSAppearance.Name?
    private var debounceTimer: Timer?

    func applicationDidFinishLaunching(_ notification: Notification) {
        // Créer un status item pour observer l'apparence de la menu bar
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        statusItem?.button?.title = "MenuBar Detector"
        statusItem?.isVisible = true

        // Observer SEULEMENT l'apparence (pas le frame pour éviter trop de déclenchements)
        statusItem?.addObserver(self,
                                forKeyPath: "button.effectiveAppearance",
                                options: [.initial, .new],
                                context: nil)
    }

    override func observeValue(forKeyPath keyPath: String?,
                               of object: Any?,
                               change: [NSKeyValueChangeKey : Any]?,
                               context: UnsafeMutableRawPointer?) {

        if keyPath == "button.effectiveAppearance" {
            // Annuler le timer précédent pour éviter les logs en rafale
            debounceTimer?.invalidate()

            // Debounce plus long (300ms) pour éviter les oscillations transitoires
            debounceTimer = Timer.scheduledTimer(withTimeInterval: 0.3, repeats: false) { [weak self] _ in
                guard let self = self,
                      let statusItem = object as? NSStatusItem,
                      let appearance = statusItem.button?.effectiveAppearance else { return }

                self.updateStatusItemForAppearance(appearance, statusItem: statusItem)
            }
        }
    }

    func updateStatusItemForAppearance(_ appearance: NSAppearance, statusItem: NSStatusItem) {
        let name = appearance.bestMatch(from: [.darkAqua, .aqua])
        let height = statusItem.button?.frame.height ?? 0

        // Éviter de logger la même apparence plusieurs fois
        guard name != lastAppearanceName else { return }

        // Double vérification après 50ms pour s'assurer que l'apparence est stable
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { [weak self] in
            guard let self = self,
                  let currentAppearance = statusItem.button?.effectiveAppearance else { return }

            let currentName = currentAppearance.bestMatch(from: [.darkAqua, .aqua])

            // Ne procéder que si l'apparence est stable et différente de la précédente
            guard currentName == name && currentName != self.lastAppearanceName else { return }

            self.lastAppearanceName = currentName

            switch currentName {
            case .darkAqua:
                print("🌙 Dark mode detected in menu bar (height: \(height))")
                statusItem.button?.title = "Dark \(Int(height))"
            case .aqua:
                print("☀️ Light mode detected in menu bar (height: \(height))")
                statusItem.button?.title = "Light \(Int(height))"
            default:
                print("❓ Unknown mode: \(currentAppearance.name.rawValue) (height: \(height))")
                statusItem.button?.title = "Unknown \(Int(height))"
            }
        }
    }

    deinit {
        // Annuler le timer
        debounceTimer?.invalidate()
        // Nettoyer l'observer
        statusItem?.removeObserver(self, forKeyPath: "button.effectiveAppearance")
    }
}

// Create app
let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.run()