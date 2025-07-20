#include "QtTrayMenu.h"
#include <QApplication>
#include "tray.h"

static QtTrayMenu *trayMenuInstance = nullptr;
static struct tray *currentTrayStruct = nullptr;

extern "C"
{

TRAY_EXPORT struct tray *tray_get_instance()
{
    return currentTrayStruct;
}

TRAY_EXPORT int tray_init(struct tray *tray)
{
    currentTrayStruct = tray;

    if (!trayMenuInstance)
    {
        trayMenuInstance = new QtTrayMenu();
    }

    return trayMenuInstance->init(tray);
}

TRAY_EXPORT int tray_loop(int blocking) {
    return trayMenuInstance ? trayMenuInstance->loop(blocking) : -1;
}

TRAY_EXPORT void tray_update(struct tray *tray)
{
    currentTrayStruct = tray;
    if (trayMenuInstance)
    {
        trayMenuInstance->update(tray);
    }
}

TRAY_EXPORT void tray_exit(void)
{
    if (trayMenuInstance)
    {
        // First signal the tray to exit
        trayMenuInstance->exit();
        
        // Process events to ensure exit signal is processed
        QCoreApplication::processEvents(QEventLoop::AllEvents, 100);
        
        // Hide the tray icon before deletion to ensure proper cleanup
        QMetaObject::invokeMethod(trayMenuInstance, [&]() {
            // Additional cleanup in the Qt thread context
            if (QApplication::instance()) {
                QApplication::instance()->processEvents(QEventLoop::AllEvents);
            }
        }, Qt::BlockingQueuedConnection);
        
        // Schedule deletion of the tray instance
        trayMenuInstance->deleteLater();
        
        // Process events multiple times with smaller timeouts to allow
        // proper cleanup of GLib context between event processing cycles
        for (int i = 0; i < 10; i++) {
            QCoreApplication::processEvents(QEventLoop::AllEvents, 100);
            // Small sleep between processing to allow background operations to complete
            if (i % 2 == 0) {
                QThread::msleep(50);
            }
        }
        
        // Final event processing with longer timeout
        QCoreApplication::processEvents(QEventLoop::AllEvents, 300);
        
        // Clear the pointer after processing events
        trayMenuInstance = nullptr;
        
        // Ensure all pending deletions are processed
        QCoreApplication::sendPostedEvents(nullptr, QEvent::DeferredDelete);
        QCoreApplication::processEvents(QEventLoop::AllEvents);
    }
}

} // extern "C"
