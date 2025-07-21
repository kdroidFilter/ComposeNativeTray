// Modified tray.h
#ifndef TRAY_H
#define TRAY_H

typedef void (*tray_menu_item_callback)(void* item);
typedef void (*tray_callback)(void* tray);
typedef void (*theme_callback)(int is_dark);

struct tray_menu_item {
    char* text;
    int disabled;
    int checked;
    tray_menu_item_callback cb;
    struct tray_menu_item* submenu;
};

struct tray {
    char* icon_filepath;
    char* tooltip;
    struct tray_menu_item* menu;
    tray_callback cb;
};

void* tray_get_instance(void);
int tray_init(struct tray* tray);
int tray_loop(int blocking);
void tray_update(struct tray* tray);
void tray_exit(void);
void tray_set_theme_callback(theme_callback cb);
int tray_is_menu_dark(void);

#endif