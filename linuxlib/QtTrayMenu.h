#ifndef TRAYMENU_H
#define TRAYMENU_H

#include <QApplication>
#include <QSystemTrayIcon>
#include <QMenu>
#include <QObject>
#include <QThread>
#include <QEventLoop>
#include <QCoreApplication>
#include "tray.h"

// All comments are in English, as requested.
class QtTrayMenu : public QObject
{
    Q_OBJECT

public:
    QtTrayMenu();
    ~QtTrayMenu();

    // Public API
    int  init(struct tray *tray);
    void update(struct tray *tray);
    int  loop(int blocking);     // blocking == 0 → non‑blocking
    void exit();                 // initiate graceful teardown

private:
    // Internal helpers
    void createMenu(struct tray_menu_item *items, QMenu *menu);
    void onTrayActivated(QSystemTrayIcon::ActivationReason reason);
    void onMenuItemTriggered();
    struct tray_menu_item *getTrayMenuItem(QAction *action);

    // Helper: run a lambda on the Qt thread and wait until it completes
    template<typename Fn>
    void runInQtThreadBlocking(Fn &&fn)
    {
        // Same thread → direct call
        if (QThread::currentThread() == this->thread() || this->thread() == nullptr) {
            fn();
            return;
        }

        // Different thread → queued invoke + local loop
        QEventLoop loop;
        auto wrapper = [&, f = std::forward<Fn>(fn)]() mutable {
            f();
            loop.quit();
        };
        QMetaObject::invokeMethod(this, wrapper, Qt::QueuedConnection);
        loop.exec();  // processes events until wrapper calls loop.quit()
    }

    // State
    QApplication        *app;
    QSystemTrayIcon     *trayIcon;
    struct tray         *trayStruct;
    bool                 continueRunning;

    signals:
        void exitRequested();
    void cleanupRequested();

private slots:
    void onExitRequested();
    void onCleanupRequested();
};

#endif // TRAYMENU_H