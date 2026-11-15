package com.limelight.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.widget.PopupMenu;

import com.limelight.R;
import com.limelight.binding.input.HostKeyboardLayout;

import java.util.HashMap;
import java.util.Map;

/** Quick access to host-side Sunshine shortcuts and local stream actions. */
public final class QuickActionsMenu {
    public interface Listener {
        void onShortcut(int vk, int modifiers);
        void onToggleKeyboard();
        void onResetZoom();
        void onDisconnect();
    }

    private static final int SUNSHINE_MODIFIERS = HostKeyboardLayout.MOD_CTRL |
            HostKeyboardLayout.MOD_ALT | HostKeyboardLayout.MOD_SHIFT;
    private static final int LOCAL_KEYBOARD = 1;
    private static final int LOCAL_RESET_ZOOM = 2;
    private static final int LOCAL_DISCONNECT = 3;

    private static final class Entry {
        final int vk;
        final int modifiers;
        final int localAction;

        Entry(int vk, int modifiers) {
            this.vk = vk;
            this.modifiers = modifiers;
            this.localAction = 0;
        }

        Entry(int localAction) {
            this.vk = 0;
            this.modifiers = 0;
            this.localAction = localAction;
        }
    }

    private int nextId = 1;
    private final Map<Integer, Entry> entries = new HashMap<>();

    private void shortcut(Menu menu, CharSequence title, int vk, int modifiers) {
        int id = nextId++;
        entries.put(id, new Entry(vk, modifiers));
        menu.add(Menu.NONE, id, Menu.NONE, title);
    }

    private void local(Menu menu, int title, int action) {
        int id = nextId++;
        entries.put(id, new Entry(action));
        menu.add(Menu.NONE, id, Menu.NONE, title);
    }

    private QuickActionsMenu() {
    }

    public static void show(Context context, View anchor, final Listener listener) {
        final QuickActionsMenu actions = new QuickActionsMenu();
        PopupMenu popup = new PopupMenu(context, anchor, Gravity.END);
        Menu menu = popup.getMenu();

        actions.local(menu, R.string.quick_action_keyboard, LOCAL_KEYBOARD);
        actions.local(menu, R.string.quick_action_reset_zoom, LOCAL_RESET_ZOOM);

        SubMenu sunshine = menu.addSubMenu(R.string.quick_menu_sunshine);
        actions.shortcut(sunshine, context.getString(R.string.quick_sunshine_cursor),
                'N', SUNSHINE_MODIFIERS);
        SubMenu displays = sunshine.addSubMenu(R.string.quick_menu_displays);
        for (int display = 1; display <= 12; display++) {
            // Sunshine intercepts Ctrl+Alt+Shift+F1 through F12 and switches the
            // captured display without forwarding that combination to the desktop.
            actions.shortcut(displays, context.getString(R.string.quick_display_number, display),
                    HostKeyboardLayout.VK_F1 + display - 1, SUNSHINE_MODIFIERS);
        }

        actions.local(menu, R.string.quick_action_disconnect, LOCAL_DISCONNECT);

        popup.setOnMenuItemClickListener(item -> {
            Entry entry = actions.entries.get(item.getItemId());
            if (entry == null) {
                return false;
            }
            if (entry.localAction != 0) {
                switch (entry.localAction) {
                    case LOCAL_KEYBOARD:
                        listener.onToggleKeyboard();
                        break;
                    case LOCAL_RESET_ZOOM:
                        listener.onResetZoom();
                        break;
                    case LOCAL_DISCONNECT:
                        listener.onDisconnect();
                        break;
                }
            }
            else {
                listener.onShortcut(entry.vk, entry.modifiers);
            }
            return true;
        });
        popup.show();
    }
}
