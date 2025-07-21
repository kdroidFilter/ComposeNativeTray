#include "QtTrayMenu.h"
#include <QApplication>
#include <QDebug>
#include <QtGlobal>
#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>
#include <QTimer>

#ifdef Q_OS_LINUX
#include <QDBusConnection>
#include <QDBusMessage>
#include <QDBusInterface>
#endif

int argc = 1;
char *argvArray[] = {(char *)"TrayMenuApp", nullptr};
bool debug = false;

// Forward declarations
bool isGnomeDesktop();
void applyGnomeDBusWorkaround();

// Function to detect the GNOME desktop environment
bool isGnomeDesktop() {
    const QString desktop = qEnvironmentVariable("XDG_CURRENT_DESKTOP");
    const QString session = qEnvironmentVariable("GNOME_DESKTOP_SESSION_ID");
    const QString gdmSession = qEnvironmentVariable("GDMSESSION");

    return desktop.contains("GNOME", Qt::CaseInsensitive) ||
           !session.isEmpty() ||
           gdmSession.contains("gnome", Qt::CaseInsensitive);
}

// DBus-based workaround for GNOME busy cursor issue
void applyGnomeDBusWorkaround() {
    if (!isGnomeDesktop()) {
        return;
    }

#ifdef Q_OS_LINUX
    // Use D-Bus to interact with GNOME Shell
    QDBusInterface shellInterface(
        "org.gnome.Shell",
        "/org/gnome/Shell",
        "org.gnome.Shell",
        QDBusConnection::sessionBus()
    );

    if (shellInterface.isValid()) {
        // Execute JavaScript commands in GNOME Shell to reset cursor and update UI
        QList<QString> commands = {
            // Reset cursor to default
            "global.display.set_cursor(Meta.Cursor.DEFAULT);",
            // Update panel to refresh UI state
            "Main.panel.queue_relayout();",
            // Force stage redraw
            "global.stage.queue_redraw();"
        };

        for (const QString &cmd : commands) {
            QList<QVariant> args;
            args << cmd;
            shellInterface.callWithArgumentList(QDBus::NoBlock, "Eval", args);
        }
    }
#endif
}

// Suppress GLib messages by redirecting stderr temporarily
void suppressGLibMessages() {
    setenv("G_MESSAGES_DEBUG", "", 1);
    setenv("G_DEBUG", "", 1);

    static bool glib_suppressed = false;
    if (!glib_suppressed) {
        static int original_stderr = dup(STDERR_FILENO);

        int dev_null = open("/dev/null", O_WRONLY);
        if (dev_null != -1) {
            dup2(dev_null, STDERR_FILENO);
            close(dev_null);
        }
        glib_suppressed = true;
    }
}

// Custom message handler to filter out specific Qt warnings
void customMessageHandler(QtMsgType type, const QMessageLogContext &context, const QString &msg)
{
    if (msg.contains("QObject::killTimer: Timers cannot be stopped from another thread") ||
        msg.contains("QObject::~QObject: Timers cannot be stopped from another thread") ||
        msg.contains("g_main_context_pop_thread_default")) {
        return;
    }

    const QByteArray localMsg = msg.toLocal8Bit();
    switch (type) {
    case QtDebugMsg:
        if (debug) fprintf(stderr, "Debug: %s (%s:%u, %s)\n", localMsg.constData(), context.file, context.line, context.function);
        break;
    case QtInfoMsg:
        fprintf(stderr, "Info: %s (%s:%u, %s)\n", localMsg.constData(), context.file, context.line, context.function);
        break;
    case QtWarningMsg:
        fprintf(stderr, "Warning: %s (%s:%u, %s)\n", localMsg.constData(), context.file, context.line, context.function);
        break;
    case QtCriticalMsg:
        fprintf(stderr, "Critical: %s (%s:%u, %s)\n", localMsg.constData(), context.file, context.line, context.function);
        break;
    case QtFatalMsg:
        fprintf(stderr, "Fatal: %s (%s:%u, %s)\n", localMsg.constData(), context.file, context.line, context.function);
        abort();
    }
}

QtTrayMenu::QtTrayMenu()
    : trayIcon(nullptr), trayStruct(nullptr), continueRunning(true), app(nullptr), createdApp(false), blockingEventLoop(nullptr)
{
    suppressGLibMessages();
    qInstallMessageHandler(customMessageHandler);

    if (QApplication::instance()) {
        app = dynamic_cast<QApplication *>(QApplication::instance());
        if (!app) {
            fprintf(stderr, "QCoreApplication is not a QApplication, please contact support.");
        }
    } else {
        app = new QApplication(argc, &argvArray[0]);
        createdApp = true;
    }

    if (debug)
        app->installEventFilter(this);
}

QtTrayMenu::~QtTrayMenu()
{
    if (trayIcon) {
        trayIcon->hide();
        trayIcon->deleteLater();
        trayIcon = nullptr;
    }

    if (app) {
        app->processEvents(QEventLoop::AllEvents, 100);
    }
}

int QtTrayMenu::init(struct tray *tray)
{
    if (trayIcon)
        return -1;

    continueRunning = true;
    this->trayStruct = tray;

    if (app->applicationName().isEmpty() || app->applicationName() == "TrayMenuApp")
        app->setApplicationName(tray->tooltip);

    trayIcon = new QSystemTrayIcon(QIcon(tray->icon_filepath));
    trayIcon->setToolTip(QString::fromUtf8(tray->tooltip));

    connect(trayIcon, &QSystemTrayIcon::activated, this, &QtTrayMenu::onTrayActivated);

    auto *menu = new QMenu;
    createMenu(tray->menu, menu);

    trayIcon->setContextMenu(menu);
    trayIcon->show();

    return 0;
}

void QtTrayMenu::update(struct tray *tray)
{
    QMetaObject::invokeMethod(this, [this, tray]() {
        this->trayStruct = tray;
        if (trayIcon) {
            auto newIcon = QIcon(tray->icon_filepath);
            if (!newIcon.isNull())
                trayIcon->setIcon(newIcon);
            trayIcon->setToolTip(QString::fromUtf8(tray->tooltip));
        }

        auto *existingMenu = trayIcon->contextMenu();
        if (existingMenu) {
            existingMenu->clear();
            createMenu(tray->menu, existingMenu);
        }

        // Apply GNOME DBus workaround after update
        QTimer::singleShot(100, []() {
            applyGnomeDBusWorkaround();
        });
    }, Qt::QueuedConnection);
}

int QtTrayMenu::loop(int blocking)
{
    if (!continueRunning) {
        return -1;
    }

    if (!app || app->closingDown()) {
        printf("Application is not in a valid state or is closing down.\n");
        return -1;
    }

    if (blocking) {
        QEventLoop localLoop;
        blockingEventLoop = &localLoop;

        while (continueRunning) {
            localLoop.processEvents(QEventLoop::AllEvents, 100);
            if (!continueRunning) break;
        }

        blockingEventLoop = nullptr;
        return -1;
    } else {
        app->processEvents();
        return 0;
    }
}

void QtTrayMenu::exit()
{
    continueRunning = false;

    if (trayIcon) {
        trayIcon->hide();
        trayIcon->deleteLater();
        trayIcon = nullptr;
    }

    if (blockingEventLoop) {
        blockingEventLoop->quit();
    }

    if (app) {
        app->processEvents(QEventLoop::AllEvents, 200);
    }
}

void QtTrayMenu::createMenu(struct tray_menu_item *items, QMenu *menu)
{
    while (items && items->text) {
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

        action->setProperty("tray_menu_item", QVariant::fromValue((void *)items));
        connect(action, &QAction::triggered, this, &QtTrayMenu::onMenuItemTriggered);

        if (items->submenu) {
            auto *submenu = new QMenu;
            createMenu(items->submenu, submenu);
            action->setMenu(submenu);
        }

        menu->addAction(action);
        ++items;
    }
}

bool QtTrayMenu::eventFilter(QObject *watched, QEvent *event)
{
    qDebug() << "Event Type:" << event->type();
    return QObject::eventFilter(watched, event);
}

void QtTrayMenu::onTrayActivated(QSystemTrayIcon::ActivationReason reason)
{
    if (reason == QSystemTrayIcon::Trigger && trayStruct && trayStruct->cb) {
        trayStruct->cb(trayStruct);

        // Apply DBus workaround after callback with delay
        QTimer::singleShot(100, []() {
            applyGnomeDBusWorkaround();
        });
    }
}

void QtTrayMenu::onMenuItemTriggered()
{
    auto *action = qobject_cast<QAction *>(sender());
    struct tray_menu_item *menuItem = getTrayMenuItem(action);

    if (menuItem && menuItem->cb) {
        menuItem->cb(menuItem);

        // Apply DBus workaround after menu callback with delay
        QTimer::singleShot(100, []() {
            applyGnomeDBusWorkaround();
        });
    }
}

struct tray_menu_item *QtTrayMenu::getTrayMenuItem(QAction *action)
{
    return reinterpret_cast<struct tray_menu_item *>(action->property("tray_menu_item").value<void *>());
}