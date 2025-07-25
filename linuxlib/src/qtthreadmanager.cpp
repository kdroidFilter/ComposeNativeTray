// File: qtthreadmanager.cpp
#include "qtthreadmanager.h"
#include <QMetaObject>

/* ------------------------------------------------------------------ *
 *  Instance unique, recréée si le QThread précédent est terminé       *
 * ------------------------------------------------------------------ */
static QtThreadManager* g_instance = nullptr;

QtThreadManager* QtThreadManager::createAndStart()
{
    auto* t = new QtThreadManager();
    t->start();

    // Attendre que QApplication soit prête
    QMutexLocker locker(&t->readyMutex);
    t->readyCond.wait(&t->readyMutex, 5000);      // max 5 s pour la sécurité
    return t;
}

QtThreadManager* QtThreadManager::instance()
{
    if (!g_instance || g_instance->isFinished()) {
        delete g_instance;                         // safe si nullptr
        g_instance = createAndStart();
    }
    return g_instance;
}

/* ------------------------------ *
 *  Arrêt propre et idempotent    *
 * ------------------------------ */
void QtThreadManager::shutdown()
{
    if (!g_instance || !g_instance->isRunning() || g_instance->m_app == nullptr)
        return;                                    // déjà arrêté

    QMetaObject::invokeMethod(g_instance->m_app, "quit", Qt::QueuedConnection);
    g_instance->wait();                            // bloc jusqu’à sortie d’event‑loop
    // g_instance reste alloué ; il sera supprimé puis recréé au prochain instance()
}

/* --------------------------------------------------------- *
 *            Corps du thread Qt : crée QApplication         *
 * --------------------------------------------------------- */
QtThreadManager::QtThreadManager() : QThread() {}

void QtThreadManager::run()
{
    int argc = 0;
    m_app = new QApplication(argc, nullptr);

    // signaler que QApplication est prête
    {
        QMutexLocker locker(&readyMutex);
        readyCond.wakeAll();
    }

    exec();                  // boucle d’événements

    delete m_app;
    m_app = nullptr;
}

/* ------------------------------------------------------------ *
 * Exécution synchrone dans le thread Qt                        *
 * ------------------------------------------------------------ */
void QtThreadManager::runBlocking(const std::function<void()>& fn)
{
    QtThreadManager* t = instance();               // assure qu’un thread tourne

    if (QThread::currentThread() == t) {           // déjà dans le bon thread
        fn();
        return;
    }

    QEventLoop loop;
    QMetaObject::invokeMethod(t,
        [&fn, &loop]() { fn(); loop.quit(); },
        Qt::QueuedConnection);

    loop.exec();                                   // bloque jusqu’à fin de fn()
}

/* ------------------------------------------------------------ *
 * Exécution asynchrone                                         *
 * ------------------------------------------------------------ */
void QtThreadManager::runAsync(const std::function<void()>& fn)
{
    QtThreadManager* t = instance();               // assure qu’un thread tourne
    QMetaObject::invokeMethod(t, [fn]{ fn(); }, Qt::QueuedConnection);
}
