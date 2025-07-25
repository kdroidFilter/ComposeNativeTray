// File: qtthreadmanager.cpp
#include "qtthreadmanager.h"
#include <QMetaObject>

QtThreadManager::QtThreadManager()
    : QThread(), readyMutex(), readyCond()
{
}

QtThreadManager* QtThreadManager::instance()
{
    static QtThreadManager* inst = []{
        auto* t = new QtThreadManager();
        t->start();
        // Wait until QApplication is created in the new thread
        QMutexLocker locker(&t->readyMutex);
        t->readyCond.wait(&t->readyMutex, 5000); // timeout after 5 s
        QThread::msleep(100); // Add this line to give time for exec() to start and avoid race conditions
        return t;
    }();
    return inst;
}

void QtThreadManager::shutdown()
{
    auto* t = instance();
    // Tell the QApplication to quit
    QMetaObject::invokeMethod(t->m_app, "quit", Qt::QueuedConnection);
    // Wait for the thread to finish
    t->wait();
}

void QtThreadManager::run()
{
    int argc = 0;
    m_app = new QApplication(argc, nullptr);

    // Signal that QApplication is ready
    {
        QMutexLocker locker(&readyMutex);
        readyCond.wakeAll();
    }

    // Run the thread’s event loop (which also drives Qt events)
    exec();

    delete m_app;
    m_app = nullptr;
}

void QtThreadManager::runBlocking(const std::function<void()>& fn) {
    if (QThread::currentThread() == this) {
        fn();
        return;
    }

    QEventLoop loop;
    QMetaObject::invokeMethod(this, [fn, &loop]() {
        fn();
        loop.quit();
    }, Qt::QueuedConnection);

    loop.exec();
}

void QtThreadManager::runAsync(const std::function<void()>& fn)
{
    QMetaObject::invokeMethod(
        this,
        [fn]{ fn(); },
        Qt::QueuedConnection
    );
}
