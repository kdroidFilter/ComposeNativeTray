#include <windows.h>
#include <shellapi.h>
#include "tray.h"

#define WM_TRAY_CALLBACK_MESSAGE (WM_USER + 1)
#define WC_TRAY_CLASS_NAME "TRAY"
#define ID_TRAY_FIRST 1000

static struct tray *tray_instance;
static WNDCLASSEX wc;
static NOTIFYICONDATA nid;
static HWND hwnd;
static HMENU hmenu = NULL;
static UINT wm_taskbarcreated;
static BOOL exit_was_called = FALSE;

static LRESULT CALLBACK _tray_wnd_proc(HWND hwnd, UINT msg, WPARAM wparam,
                                       LPARAM lparam) {
  switch (msg) {
  case WM_CLOSE:
    DestroyWindow(hwnd);
    return 0;
  case WM_DESTROY:
    PostQuitMessage(0);
    return 0;
  case WM_TRAY_CALLBACK_MESSAGE:
    if (lparam == WM_LBUTTONUP && tray_instance->cb != NULL) {
      tray_instance->cb(tray_get_instance());
      return 0;
    }
    if (lparam == WM_LBUTTONUP || lparam == WM_RBUTTONUP) {
      POINT p;
      GetCursorPos(&p);
      SetForegroundWindow(hwnd);
      WORD cmd = TrackPopupMenu(hmenu, TPM_LEFTALIGN | TPM_RIGHTBUTTON |
                                       TPM_RETURNCMD | TPM_NONOTIFY,
                                p.x, p.y, 0, hwnd, NULL);
      SendMessage(hwnd, WM_COMMAND, cmd, 0);
      return 0;
    }
    break;
  case WM_COMMAND:
    if (wparam >= ID_TRAY_FIRST) {
      MENUITEMINFO item = {
          .cbSize = sizeof(MENUITEMINFO), .fMask = MIIM_ID | MIIM_DATA,
      };
      if (GetMenuItemInfo(hmenu, (UINT)wparam, FALSE, &item)) {
        struct tray_menu_item *menu = (struct tray_menu_item *)item.dwItemData;
        if (menu != NULL && menu->cb != NULL) {
          menu->cb(menu);
        }
      }
      return 0;
    }
    break;
  }

  if (msg == wm_taskbarcreated) {
    Shell_NotifyIcon(NIM_ADD, &nid);
    return 0;
  }

  return DefWindowProc(hwnd, msg, wparam, lparam);
}

static HMENU _tray_menu_item(struct tray_menu_item *m, UINT *id) {
  HMENU hmenu = CreatePopupMenu();
  for (; m != NULL && m->text != NULL; m++, (*id)++) {
    if (strcmp(m->text, "-") == 0) {
      InsertMenu(hmenu, *id, MF_SEPARATOR, TRUE, "");
    } else {
      MENUITEMINFO item;
      memset(&item, 0, sizeof(item));
      item.cbSize = sizeof(MENUITEMINFO);
      item.fMask = MIIM_ID | MIIM_TYPE | MIIM_STATE | MIIM_DATA;
      item.fType = 0;
      item.fState = 0;
      if (m->submenu != NULL) {
        item.fMask = item.fMask | MIIM_SUBMENU;
        item.hSubMenu = _tray_menu_item(m->submenu, id);
      }
      if (m->disabled) {
        item.fState |= MFS_DISABLED;
      }
      if (m->checked) {
        item.fState |= MFS_CHECKED;
      }
      item.wID = *id;
      item.dwTypeData = (LPSTR)m->text;
      item.dwItemData = (ULONG_PTR)m;

      InsertMenuItem(hmenu, *id, TRUE, &item);
    }
  }
  return hmenu;
}

struct tray * tray_get_instance() {
  return tray_instance;
}

int tray_init(struct tray *tray) {
  OutputDebugStringA("Init started");
  wm_taskbarcreated = RegisterWindowMessage("TaskbarCreated");
  OutputDebugStringA("Init 2");

  if (exit_was_called) {
    exit_was_called = FALSE;
  }

  memset(&wc, 0, sizeof(wc));
  OutputDebugStringA("Memset done");
  wc.cbSize = sizeof(WNDCLASSEX);
  wc.lpfnWndProc = _tray_wnd_proc;
  wc.hInstance = GetModuleHandle(NULL);
  wc.lpszClassName = WC_TRAY_CLASS_NAME;

  if (!RegisterClassEx(&wc)) {
    if (GetLastError() != ERROR_CLASS_ALREADY_EXISTS) {
      return -1;
    }
  }

  hwnd = CreateWindowEx(0, WC_TRAY_CLASS_NAME, NULL, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  if (hwnd == NULL) {
    return -1;
  }
  UpdateWindow(hwnd);

  memset(&nid, 0, sizeof(nid));
  nid.cbSize = sizeof(NOTIFYICONDATA);
  nid.hWnd = hwnd;
  nid.uID = 0;
  nid.uFlags = NIF_ICON | NIF_MESSAGE;
  nid.uCallbackMessage = WM_TRAY_CALLBACK_MESSAGE;
  Shell_NotifyIcon(NIM_ADD, &nid);

  tray_update(tray);
  return 0;
}

int tray_loop(int blocking) {
  MSG msg;
  if (blocking) {
    GetMessage(&msg, hwnd, 0, 0);
  } else {
    PeekMessage(&msg, hwnd, 0, 0, PM_REMOVE);
  }
  if (msg.message == WM_QUIT) {
    return -1;
  }
  TranslateMessage(&msg);
  DispatchMessage(&msg);
  return 0;
}

void tray_update(struct tray *tray) {
  HMENU prevmenu = hmenu;
  UINT id = ID_TRAY_FIRST;
  hmenu = _tray_menu_item(tray->menu, &id);
  SendMessage(hwnd, WM_INITMENUPOPUP, (WPARAM)hmenu, 0);
  HICON icon;
  ExtractIconEx(tray->icon_filepath, 0, NULL, &icon, 1);
  if (nid.hIcon) {
    DestroyIcon(nid.hIcon);
  }
  nid.hIcon = icon;
  if (tray->tooltip != 0 && strlen(tray->tooltip) > 0) {
    strncpy(nid.szTip, tray->tooltip, sizeof(nid.szTip));
    nid.uFlags |= NIF_TIP;
  }
  Shell_NotifyIcon(NIM_MODIFY, &nid);

  if (prevmenu != NULL) {
    DestroyMenu(prevmenu);
  }

  tray_instance = tray;
}

void tray_exit(void) {
  if (exit_was_called != FALSE) {
    return;
  }
  exit_was_called = TRUE;
  Shell_NotifyIcon(NIM_DELETE, &nid);
  if (nid.hIcon != 0) {
    DestroyIcon(nid.hIcon);
  }
  if (hmenu != 0) {
    DestroyMenu(hmenu);
  }
  if (hwnd != NULL) {
    DestroyWindow(hwnd);
    hwnd = NULL;
  }
  UnregisterClass(WC_TRAY_CLASS_NAME, GetModuleHandle(NULL));
}


void tray_get_notification_icons_position(int* x, int* y) {
  RECT rect;

  // Get the handle to the notification icons tray ("TrayNotifyWnd")
  HWND hwndTray = FindWindow(TEXT("Shell_TrayWnd"), NULL);
  HWND hwndNotification = FindWindowEx(hwndTray, NULL, TEXT("TrayNotifyWnd"), NULL);

  if (hwndNotification) {
    GetWindowRect(hwndNotification, &rect);
    *x = rect.left;
    *y = rect.top;
  } else {
    // If the notification area window was not found, set position to default (0, 0).
    *x = 0;
    *y = 0;
  }
}

const char* tray_get_notification_icons_region() {
  RECT rect;
  POINT trayPosition;

  // Get the handle to the notification icons tray ("TrayNotifyWnd")
  HWND hwndTray = FindWindow(TEXT("Shell_TrayWnd"), NULL);
  HWND hwndNotification = FindWindowEx(hwndTray, NULL, TEXT("TrayNotifyWnd"), NULL);

  if (hwndNotification) {
    GetWindowRect(hwndNotification, &rect);
    trayPosition.x = rect.left;
    trayPosition.y = rect.top;
  } else {
    // If the notification area window was not found, set position to default (0, 0).
    trayPosition.x = 0;
    trayPosition.y = 0;
  }

  // Determine the position region: top-left, top-right, bottom-left, bottom-right
  HMONITOR hMonitor = MonitorFromWindow(hwndNotification, MONITOR_DEFAULTTOPRIMARY);
  MONITORINFO mi;
  mi.cbSize = sizeof(MONITORINFO);
  GetMonitorInfo(hMonitor, &mi);

  int midX = (mi.rcMonitor.right - mi.rcMonitor.left) / 2;
  int midY = (mi.rcMonitor.bottom - mi.rcMonitor.top) / 2;

  if (trayPosition.x < midX && trayPosition.y < midY) {
    return "top-left";
  } else if (trayPosition.x >= midX && trayPosition.y < midY) {
    return "top-right";
  } else if (trayPosition.x < midX && trayPosition.y >= midY) {
    return "bottom-left";
  } else {
    return "bottom-right";
  }
}
