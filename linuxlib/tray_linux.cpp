#include "QtTrayMenu.h"
#include <QApplication>
#include <QThread>
#include <memory>
#include <atomic>
#include <QTimer>

#include "tray.h"

static std::unique_ptr<QtTrayMenu> trayMenuInstance;
static struct tray *currentTrayStruct = nullptr;
static std::atomic<bool> trayActive(false);
static QThread* trayThread = nullptr;

// ---------------------------------------------------------------------
// Helper: Properly clean up Qt objects
// ---------------------------------------------------------------------
static void cleanupTrayMenu()
{
    if (!trayMenuInstance) return;

    // If we're on a different thread than the tray menu, we need to be careful
    if (trayMenuInstance->thread() != QThread::currentThread()) {
        // Use a blocking queued connection to ensure cleanup completes
        QMetaObject::invokeMethod(
            trayMenuInstance.get(),
            [&]() {
                trayMenuInstance.reset();
            },
            Qt::BlockingQueuedConnection);
    } else {
        trayMenuInstance.reset();
    }
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

    // Clean up any existing instance
    if (trayMenuInstance && trayActive.load()) {
        // Exit the existing tray
        trayMenuInstance->exit();

        // Wait a bit for cleanup to complete
        QThread::msleep(100);

        // Clean up
        cleanupTrayMenu();
        trayActive.store(false);

        // Give Qt time to fully clean up
        QThread::msleep(100);
    }

    // Ensure Qt application is running
    QtAppManager::instance().ensureRunning();

    // Create new instance
    trayMenuInstance = std::make_unique<QtTrayMenu>();

    // Initialize on the Qt thread
    int result = trayMenuInstance->init(tray);

    if (result == 0) {
        trayActive.store(true);
    }

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
    if (trayMenuInstance && trayActive.load()) {
        trayMenuInstance->update(tray);
    }
}

TRAY_EXPORT void tray_exit(void)
{
    if (trayMenuInstance && trayActive.load()) {
        trayActive.store(false);

        // Call exit on the tray menu
        trayMenuInstance->exit();

        // Give some time for the exit to process
        QThread::msleep(50);

        // Clean up
        cleanupTrayMenu();
    }
}

} // extern "C"