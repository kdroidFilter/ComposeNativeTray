// File: qtthreadmanager.h
#pragma once

#include <QThread>
#include <QApplication>
#include <QMutex>
#include <QWaitCondition>
#include <functional>
#include <QEventLoop>
#include <QSemaphore>  // Optional, but useful for alternatives

class QtThreadManager : public QThread
{
    Q_OBJECT
public:
    static QtThreadManager* instance();
    static void shutdown();

    // Execute fn in the Qt thread and block until it finishes
    void runBlocking(const std::function<void()>& fn);

    // Execute fn in the Qt thread asynchronously
    void runAsync(const std::function<void()>& fn);

    // Access to the QApplication instance
    QApplication* app() const { return m_app; }

protected:
    void run() override;  // creates QApplication and starts the event loop

private:
    QtThreadManager();
    ~QtThreadManager() override = default;

    QApplication* m_app = nullptr;
    QMutex        readyMutex;
    QWaitCondition readyCond;
};
