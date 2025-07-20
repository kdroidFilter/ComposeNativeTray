#include "QtTrayMenu.h"
#include <QApplication>
#include <QThread>
#include <memory>
#include <atomic>

#include "tray.h"

static std::unique_ptr<QtTrayMenu> trayMenuInstance;
static struct tray *currentTrayStruct = nullptr;
static std::atomic<bool> trayActive(false);

// ---------------------------------------------------------------------
// Helper: delete the QtTrayMenu in the correct (Qt) thread
// ---------------------------------------------------------------------
static void deleteTrayMenuDeferred()
{
    if (!trayMenuInstance) return;

    // Release ownership but keep the raw pointer
    QtTrayMenu *raw = trayMenuInstance.release();

    // Ask Qt to destroy it in its own thread
    QMetaObject::invokeMethod(
        raw,
        [raw]() { raw->deleteLater(); },
        Qt::QueuedConnection);
}

// ---------------------------------------------------------------------
//  C  API
// ---------------------------------------------------------------------
extern "C"
{

TRAY_EXPORT struct tray *tray_get_instance()
{
    return currentTrayStruct;
}

TRAY_EXPORT int tray_init(struct tray *tray)
{
    currentTrayStruct = tray;

    // --- Détruire l’instance précédente proprement ------------------
    if (trayMenuInstance && trayActive.load()) {

        // 1) nettoyage interne (cache l’icône, quitte l’event‑loop)
        // Now exit() uses blocking invokes, so it waits for completion
        trayMenuInstance->exit();

        // No need for iterative processEvents; blocking invoke handles synchronization

        // 3) destruction différée, thread‑safe
        deleteTrayMenuDeferred();
        trayActive.store(false);

        // 4) petite pause pour s’assurer que tout est vraiment détruit (reduced)
        QThread::msleep(20);
    }

    // --- Nouvelle instance ------------------------------------------
    trayMenuInstance = std::make_unique<QtTrayMenu>();
    int result = trayMenuInstance->init(tray);
    if (result == 0) trayActive.store(true);
    return result;
}

TRAY_EXPORT int tray_loop(int blocking)
{
    if (!trayMenuInstance || !trayActive.load()) return -1;
    return trayMenuInstance->loop(blocking);
}

TRAY_EXPORT void tray_update(struct tray *tray)
{
    currentTrayStruct = tray;
    if (trayMenuInstance && trayActive.load())
        trayMenuInstance->update(tray);
}

TRAY_EXPORT void tray_exit(void)
{
    if (trayMenuInstance && trayActive.load()) {
        trayMenuInstance->exit();
        trayActive.store(false);
        // la destruction effective sera déclenchée par deleteLater()
        deleteTrayMenuDeferred();
    }
}

} // extern "C"