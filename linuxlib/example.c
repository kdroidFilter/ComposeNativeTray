#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include "tray.h"

#define TRAY_ICON1 "icon-24px.png"
#define TRAY_ICON2 "icon2-24px.png"

void window_cb() {
  printf("window cb: this is where you would make a window visible.\n");
}

void toggle_cb(struct tray_menu_item *item) {
  printf("toggle cb\n");
  item->checked = !item->checked;
  struct tray* tray = tray_get_instance();
  if (tray != NULL) tray_update(tray);
}

void hello_cb(struct tray_menu_item *item) {
  (void)item;
  printf("hello cb: changing icon\n");
  struct tray* tray = tray_get_instance();
  if (tray == NULL) return;
  if (strcmp(tray->icon_filepath, TRAY_ICON1) == 0) {
    tray->icon_filepath = TRAY_ICON2;
  } else {
    tray->icon_filepath = TRAY_ICON1;
  }
  tray_update(tray);
}

void quit_cb(struct tray_menu_item *item) {
  (void)item;
  printf("quit cb\n");
  tray_exit();
}

void submenu_cb(struct tray_menu_item *item) {
  (void)item;
  printf("submenu: clicked on %s\n", item->text);
}

// Test tray init
struct tray tray = {
    .icon_filepath = TRAY_ICON1,
    .tooltip = "Tray",
    .cb = window_cb,
    .menu =
        (struct tray_menu_item[]) {
            {.text = "Change Icon", .cb = hello_cb},
            {.text = "Checked", .checked = 1, .cb = toggle_cb},
            {.text = "Disabled", .disabled = 1},
            {.text = "-"},
            {.text = "SubMenu",
             .submenu =
             (struct tray_menu_item[]) {
                     {.text = "FIRST", .checked = 1, .cb = submenu_cb},
                     {.text = "SECOND",
                      .submenu =
                          (struct tray_menu_item[]) {
                              {.text = "THIRD",
                               .submenu =
                                   (struct tray_menu_item[]) {
                                       {.text = "7", .cb = submenu_cb},
                                       {.text = "-"},
                                       {.text = "8", .cb = submenu_cb},
                                       {.text = NULL}}},
                              {.text = "FOUR",
                               .submenu =
                                   (struct tray_menu_item[]) {
                                       {.text = "5", .cb = submenu_cb},
                                       {.text = "6", .cb = submenu_cb},
                                       {.text = NULL}}},
                              {.text = NULL}}},
                     {.text = NULL}}},
            {.text = "-"},
            {.text = "Quit", .cb = quit_cb},
            {.text = NULL}},
};

int main(int argc, char **argv) {
  if (tray_init(&tray) < 0) {
    printf("failed to create tray\n");
    return 1;
  }
  int blocking = 0;
  while (tray_loop(blocking) == 0) {
    if (!blocking) usleep(100000);
    printf("iteration\n");
  }
  return 0;
}