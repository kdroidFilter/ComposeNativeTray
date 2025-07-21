#ifndef TRAYMENU_H
#define TRAYMENU_H

#include <QSystemTrayIcon>
#include <QMenu>
#include <QObject>
#include <QThread>
#include "tray.h"
#include <QEventLoop>

class QtTrayMenu : public QObject
{
    Q_OBJECT

public:
    QtTrayMenu();
    ~QtTrayMenu();
    virtual bool eventFilter(QObject *watched, QEvent *event) override;
    int init(struct tray *tray);
    void update(struct tray *tray);
    int loop(int blocking);

public:
    Q_INVOKABLE void exit();  // Make it invokable but keep it in public section

private:
    void createMenu(struct tray_menu_item *items, QMenu *menu);
    void onTrayActivated(QSystemTrayIcon::ActivationReason reason);
    void onMenuItemTriggered();
    QApplication *app;
    QSystemTrayIcon *trayIcon;
    struct tray *trayStruct;
    bool continueRunning;
    struct tray_menu_item *getTrayMenuItem(QAction *action);
    bool createdApp;
    QEventLoop *blockingEventLoop;

signals:
    // Remove exitRequested signal as it's no longer needed
    // void exitRequested();

private slots:
    // Remove onExitRequested slot
    // void onExitRequested();
};

#endif // TRAYMENU_H