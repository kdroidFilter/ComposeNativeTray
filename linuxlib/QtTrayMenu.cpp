#include "QtTrayMenu.h"

#include <atomic>
#include <QIcon>
#include <QThread>

//----------------------------------------
// Construction / Destruction
//----------------------------------------
QtTrayMenu::QtTrayMenu()
    : trayIcon(nullptr)
    , trayStruct(nullptr)
    , continueRunning(true)
    , app(nullptr)
{
    // Use QtAppManager to ensure proper Qt initialization
    app = QtAppManager::instance().getApp();
    if (!app) {
        qDebug() << "Failed to get QApplication instance";
        return;
    }

    // Move this QObject to the Qt GUI thread
    this->moveToThread(app->thread());

    // Use Qt::QueuedConnection for thread-safe signal handling
    connect(this, &QtTrayMenu::initRequested,
            this, &QtTrayMenu::onInitRequested,
            Qt::QueuedConnection);

    connect(this, &QtTrayMenu::updateRequested,
            this, &QtTrayMenu::onUpdateRequested,
            Qt::QueuedConnection);

    connect(this, &QtTrayMenu::cleanupRequested,
            this, &QtTrayMenu::onCleanupRequested,
            Qt::QueuedConnection);

    connect(this, &QtTrayMenu::exitRequested,
            this, &QtTrayMenu::onExitRequested,
            Qt::QueuedConnection);
}

QtTrayMenu::~QtTrayMenu()
{
    // Ensure cleanup happens in the Qt thread
    if (trayIcon) {
        if (QThread::currentThread() == app->thread()) {
            // Already on Qt thread, cleanup directly
            onCleanupRequested();
        } else {
            // Use blocking invocation for cleanup
            QMetaObject::invokeMethod(this, &QtTrayMenu::onCleanupRequested,
                                    Qt::BlockingQueuedConnection);
        }
    }
}

//----------------------------------------
// Public API
//----------------------------------------
int QtTrayMenu::init(struct tray *tray)
{
    if (!app) {
        return -1;
    }

    // Store the tray pointer for later use
    tempTrayStruct = tray;

    // If we're on the Qt thread, initialize directly
    if (QThread::currentThread() == app->thread()) {
        return onInitRequested();
    }

    // For cross-thread initialization, we need to use invokeMethod
    // and wait for completion without using QEventLoop
    std::atomic<bool> completed(false);
    std::atomic<int> result(-1);

    QMetaObject::invokeMethod(this, [this, &result, &completed]() {
        result = onInitRequested();
        completed = true;
    }, Qt::QueuedConnection);

    // Wait for completion (busy wait with sleep to avoid blocking)
    while (!completed) {
        QThread::msleep(10);
    }

    return result;
}

void QtTrayMenu::update(struct tray *tray)
{
    if (!trayIcon || !app) return;

    // Store the tray pointer for the update
    tempTrayStruct = tray;

    // Use signal to ensure execution on Qt thread
    emit updateRequested();
}

int QtTrayMenu::loop(int blocking)
{
    if (!app) {
        return -1;
    }
    if (!continueRunning) return -1;

    try {
        if (blocking) {
            // For blocking mode, we need to ensure we're on the Qt thread
            if (QThread::currentThread() != app->thread()) {
                // If not on Qt thread, we can't run exec() directly
                return -1;
            }
            return app->exec();
        } else {
            // Non-blocking mode: process events
            app->processEvents(QEventLoop::AllEvents, 10);
            return continueRunning ? 0 : -1;
        }
    } catch (...) {
        return -1;
    }
}

void QtTrayMenu::exit()
{
    continueRunning = false;
    emit exitRequested();
}

//----------------------------------------
// Private slots (executed on Qt thread)
//----------------------------------------
int QtTrayMenu::onInitRequested()
{
    if (trayIcon) {
        return -1; // Already initialized
    }

    trayStruct = tempTrayStruct;
    tempTrayStruct = nullptr;

    if (!trayStruct) {
        return -1;
    }

    // Set application name if not already set
    if (app->applicationName().isEmpty() || app->applicationName() == "TrayMenuApp") {
        app->setApplicationName(trayStruct->tooltip ? trayStruct->tooltip : "System Tray");
    }

    // Create the tray icon
    trayIcon = new QSystemTrayIcon(this);

    if (trayStruct->icon_filepath) {
        QIcon icon(trayStruct->icon_filepath);
        if (!icon.isNull()) {
            trayIcon->setIcon(icon);
        }
    }

    if (trayStruct->tooltip) {
        trayIcon->setToolTip(QString::fromUtf8(trayStruct->tooltip));
    }

    connect(trayIcon, &QSystemTrayIcon::activated,
            this, &QtTrayMenu::onTrayActivated);

    // Create context menu
    if (trayStruct->menu) {
        auto *menu = new QMenu();
        createMenu(trayStruct->menu, menu);
        trayIcon->setContextMenu(menu);
    }

    trayIcon->show();
    app->processEvents();

    return 0;
}

void QtTrayMenu::onUpdateRequested()
{
    if (!trayIcon) return;

    trayStruct = tempTrayStruct;
    tempTrayStruct = nullptr;

    if (!trayStruct) return;

    // Update icon
    if (trayStruct->icon_filepath) {
        QIcon newIcon(trayStruct->icon_filepath);
        if (!newIcon.isNull()) {
            trayIcon->setIcon(newIcon);
        }
    }

    // Update tooltip
    if (trayStruct->tooltip) {
        trayIcon->setToolTip(QString::fromUtf8(trayStruct->tooltip));
    }

    // Update menu
    if (trayStruct->menu) {
        if (auto *existingMenu = trayIcon->contextMenu()) {
            existingMenu->clear();
            createMenu(trayStruct->menu, existingMenu);
        }
    }

    app->processEvents();
}

void QtTrayMenu::onCleanupRequested()
{
    if (!trayIcon) return;

    trayIcon->hide();

    if (auto *menu = trayIcon->contextMenu()) {
        trayIcon->setContextMenu(nullptr);
        delete menu;
    }

    delete trayIcon;
    trayIcon = nullptr;
}

void QtTrayMenu::onExitRequested()
{
    continueRunning = false;

    // Cleanup first
    onCleanupRequested();

    // Request application quit
    if (app && !app->closingDown()) {
        app->quit();
    }
}

//----------------------------------------
// Internal helpers
//----------------------------------------
void QtTrayMenu::createMenu(struct tray_menu_item *items, QMenu *menu)
{
    if (!items || !menu) return;

    while (items && items->text) {
        // Separator
        if (QString::fromUtf8(items->text) == "-") {
            menu->addSeparator();
            ++items;
            continue;
        }

        auto *action = new QAction(QString::fromUtf8(items->text), menu);
        action->setDisabled(items->disabled == 1);

        const bool isCheckable = (items->checked == 0 || items->checked == 1);
        action->setCheckable(isCheckable);
        if (isCheckable) {
            action->setChecked(items->checked == 1);
        }

        action->setProperty("tray_menu_item",
                            QVariant::fromValue(static_cast<void *>(items)));

        connect(action, &QAction::triggered,
                this, &QtTrayMenu::onMenuItemTriggered);

        // Submenu
        if (items->submenu) {
            auto *submenu = new QMenu(menu);
            createMenu(items->submenu, submenu);
            action->setMenu(submenu);
        }

        menu->addAction(action);
        ++items;
    }
}

void QtTrayMenu::onTrayActivated(QSystemTrayIcon::ActivationReason reason)
{
    if (reason == QSystemTrayIcon::Trigger && trayStruct && trayStruct->cb) {
        // Execute callback directly - it should be quick
        // If the callback needs to do heavy work, it should spawn its own thread
        trayStruct->cb(trayStruct);
        app->processEvents();
    }
}

void QtTrayMenu::onMenuItemTriggered()
{
    auto *action = qobject_cast<QAction *>(sender());
    if (!action) return;

    auto *item = getTrayMenuItem(action);
    if (!item || !item->cb) return;

    if (action->isCheckable()) {
        item->checked = action->isChecked() ? 1 : 0;
    }

    // Execute callback directly
    item->cb(item);
    app->processEvents();
}

struct tray_menu_item *QtTrayMenu::getTrayMenuItem(QAction *action)
{
    if (!action) return nullptr;
    return static_cast<tray_menu_item *>(
        action->property("tray_menu_item").value<void *>());
}