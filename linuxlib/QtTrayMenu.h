#ifndef TRAYMENU_H
#define TRAYMENU_H

#include <QApplication>
#include <QSystemTrayIcon>
#include <QMenu>
#include <QObject>
#include <QThread>
#include <QEventLoop>
#include <QCoreApplication>
#include <QTimer>
#include <QDebug>
#include "tray.h"
#include "QtAppManager.h"

class QtTrayMenu : public QObject
{
    Q_OBJECT

public:
    QtTrayMenu();
    ~QtTrayMenu();

    // Public API
    int  init(struct tray *tray);
    void update(struct tray *tray);
    int  loop(int blocking);
    void exit();

signals:
    // Signals for thread-safe operations
    void initRequested();
    void updateRequested();
    void exitRequested();
    void cleanupRequested();

private slots:
    // Slots that execute on Qt thread
    int onInitRequested();
    void onUpdateRequested();
    void onExitRequested();
    void onCleanupRequested();
    void onTrayActivated(QSystemTrayIcon::ActivationReason reason);
    void onMenuItemTriggered();

private:
    // Internal helpers
    void createMenu(struct tray_menu_item *items, QMenu *menu);
    struct tray_menu_item *getTrayMenuItem(QAction *action);

    // State
    QApplication        *app;
    QSystemTrayIcon     *trayIcon;
    struct tray         *trayStruct;
    struct tray         *tempTrayStruct; // Temporary storage for cross-thread communication
    bool                 continueRunning;
};

#endif // TRAYMENU_H