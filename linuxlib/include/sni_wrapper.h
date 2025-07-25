#ifndef SNI_WRAPPER_H
#define SNI_WRAPPER_H

#ifdef __GNUC__
#define EXPORT __attribute__((visibility("default")))
#else
#define EXPORT
#endif

#ifdef __cplusplus
#include <QObject>
#include <QApplication>

// Déclaration anticipée
class StatusNotifierItem;

class SNIWrapperManager : public QObject {
    Q_OBJECT
public:
    static SNIWrapperManager* instance();
    static void shutdown();
    static SNIWrapperManager* s_instance;

    QApplication* app;

    ~SNIWrapperManager() override;
    void startEventLoop();
    StatusNotifierItem* createSNI(const char* id);
    void destroySNI(StatusNotifierItem* sni);
    void processEvents();

private:
    SNIWrapperManager();
};
#endif

#ifdef __cplusplus
extern "C" {
#endif

/* Callback function types */
typedef void (*ActivateCallback)(int x, int y, void* user_data);
typedef void (*SecondaryActivateCallback)(int x, int y, void* user_data);
typedef void (*ScrollCallback)(int delta, int orientation, void* user_data); // 0: vertical, 1: horizontal
typedef void (*ActionCallback)(void* user_data);

/* System tray initialization and cleanup */
EXPORT int  init_tray_system(void);
EXPORT void shutdown_tray_system(void);

/* Tray creation and destruction */
EXPORT void* create_tray(const char* id);
EXPORT void  destroy_handle(void* handle);

/* Tray property setters */
EXPORT void set_title(void* handle, const char* title);
EXPORT void set_status(void* handle, const char* status);
EXPORT void set_icon_by_name(void* handle, const char* name);
EXPORT void set_icon_by_path(void* handle, const char* path);
EXPORT void update_icon_by_path(void* handle, const char* path);
EXPORT void set_tooltip_title(void* handle, const char* title);
EXPORT void set_tooltip_subtitle(void* handle, const char* subTitle);

/* Menu creation and management */
EXPORT void* create_menu(void);
EXPORT void  destroy_menu(void* menu_handle);            // NEW helper (optional)
EXPORT void  set_context_menu(void* handle, void* menu);
EXPORT void* add_menu_action(void* menu_handle, const char* text, ActionCallback cb, void* data);
EXPORT void* add_disabled_menu_action(void* menu_handle, const char* text, ActionCallback cb, void* data);
EXPORT void  add_checkable_menu_action(void* menu_handle, const char* text, int checked, ActionCallback cb, void* data);
EXPORT void  add_menu_separator(void* menu_handle);
EXPORT void* create_submenu(void* menu_handle, const char* text);
EXPORT void  set_menu_item_text(void* menu_item_handle, const char* text);
EXPORT void  set_menu_item_enabled(void* menu_item_handle, int enabled);
EXPORT void  remove_menu_item(void* menu_handle, void* menu_item_handle);

/* Tray event callbacks */
EXPORT void set_activate_callback(void* handle, ActivateCallback cb, void* data);
EXPORT void set_secondary_activate_callback(void* handle, SecondaryActivateCallback cb, void* data);
EXPORT void set_scroll_callback(void* handle, ScrollCallback cb, void* data);

/* Notifications */
EXPORT void show_notification(void* handle, const char* title, const char* msg, const char* iconName, int secs);

/* Event loop management */
EXPORT int  sni_exec(void);
EXPORT void sni_process_events(void);

#ifdef __cplusplus
}
#endif

#endif // SNI_WRAPPER_H