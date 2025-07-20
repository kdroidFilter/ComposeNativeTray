// QtAppManager.cpp
#include "QtAppManager.h"
#include <QCoreApplication>
#include <QDebug>

QtAppManager& QtAppManager::instance() {
    static QtAppManager instance;
    return instance;
}

QtAppManager::QtAppManager()
    : app(nullptr), initialized(false), running(false) {
    // Start the thread immediately upon construction
    start();
    // Wait for initialization (with timeout)
    QMutexLocker locker(&mutex);
    if (!initCondition.wait(&mutex, 2000)) {  // 2-second timeout
        qWarning() << "Timeout waiting for Qt initialization";
    }
}

QtAppManager::~QtAppManager() {
    if (running && app) {
        app->quit();
    }
    quit();
    if (!wait(2000)) {  // Wait with timeout
        qWarning() << "Timeout waiting for Qt thread to finish";
        terminate();  // Force terminate if needed (use cautiously)
    }
}

void QtAppManager::run() {
    QMutexLocker locker(&mutex);

    if (QApplication::instance()) {
        app = qobject_cast<QApplication*>(QApplication::instance());
    } else {
        static int argc = 1;
        static char app_name[] = "TrayMenuApp";
        static char *argv[] = {app_name, nullptr};
        app = new QApplication(argc, argv);
        app->setQuitOnLastWindowClosed(false);
    }

    if (!app) {
        qWarning() << "Failed to create QApplication";
        initCondition.wakeAll();
        return;
    }

    initialized = true;
    running = true;
    initCondition.wakeAll();  // Signal that init is complete

    locker.unlock();  // Unlock before exec() to avoid deadlock
    app->exec();
    running = false;
}

QApplication* QtAppManager::getApp() {
    QMutexLocker locker(&mutex);
    if (!initialized) {
        if (!initCondition.wait(&mutex, 1000)) {
            qWarning() << "Timeout waiting for app";
        }
    }
    return app;
}

bool QtAppManager::isReady() {
    QMutexLocker locker(&mutex);
    return initialized && app;
}

void QtAppManager::ensureRunning() {
    if (!running || !app) {
        // Restart if needed (though singleton prevents multiples)
        if (!isRunning()) {
            start();
        }
    }
}