package com.limelight.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.binding.input.HostKeyboardLayout;

/**
 * RealVNC-like keyboard helpers:
 *  - a draggable floating button opens the quick actions side panel.
 *  - an extra keys bar shown right above the soft keyboard with Esc/Tab/Ctrl/Alt/AltGr/Win,
 *    arrows, navigation keys and F1-F12. Modifiers are sticky: tap = next key only,
 *    long press = locked until tapped again.
 */
public class KeyboardToolbar {
    public interface Callbacks {
        void onToggleKeyboardRequested();
        void onHideKeyboardRequested();
        void onResetZoomRequested();
        void onQuickActionsRequested(View anchor);
        /** Send a full press (down+up) of a VK with the current sticky modifiers applied. */
        void onExtraKey(int vk);
    }

    public static final int MOD_CTRL = HostKeyboardLayout.MOD_CTRL, MOD_ALT = HostKeyboardLayout.MOD_ALT,
            MOD_ALTGR = HostKeyboardLayout.MOD_ALTGR, MOD_WIN = HostKeyboardLayout.MOD_WIN,
            MOD_SHIFT = HostKeyboardLayout.MOD_SHIFT;

    private static final int COLOR_BG = 0xB0202020;
    private static final int COLOR_KEY = 0xFF3A3A3A;
    private static final int COLOR_LATCHED = 0xFF1E88E5;
    private static final int COLOR_LOCKED = 0xFFE53935;

    private final Context context;
    private final FrameLayout root;
    private final Callbacks callbacks;
    private final float density;

    private TextView menuFab;
    private HorizontalScrollView bar;

    private int latched = 0; // one-shot
    private int locked = 0;
    private final TextView[] modViews = new TextView[32];

    public KeyboardToolbar(Context context, FrameLayout root, Callbacks callbacks, boolean showFab) {
        this.context = context;
        this.root = root;
        this.callbacks = callbacks;
        this.density = context.getResources().getDisplayMetrics().density;
        if (showFab) {
            createQuickActionsFab();
        }
        createBar();
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private GradientDrawable roundRect(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    @SuppressLint("ClickableViewAccessibility")
    private TextView createFloatingButton(String label, float yDp, View.OnClickListener click,
                                          Runnable longPressAction) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextSize(22);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x80000000);
        bg.setStroke(dp(1), 0x80FFFFFF);
        button.setBackground(bg);
        button.setAlpha(0.6f);
        int size = dp(48);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.START);
        button.setLayoutParams(lp);
        button.setTranslationX(dp(8));
        button.setTranslationY(dp(yDp));

        final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        final int longPress = ViewConfiguration.getLongPressTimeout();
        button.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY, startTx, startTy;
            boolean dragging, longPressed;
            final Runnable longPressRunnable = new Runnable() {
                @Override
                public void run() {
                    longPressed = true;
                    button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    if (longPressAction != null) {
                        longPressAction.run();
                    }
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        startTx = v.getTranslationX();
                        startTy = v.getTranslationY();
                        dragging = longPressed = false;
                        v.setAlpha(1.0f);
                        if (longPressAction != null) {
                            v.postDelayed(longPressRunnable, longPress);
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - downRawX, dy = e.getRawY() - downRawY;
                        if (!dragging && Math.hypot(dx, dy) > touchSlop) {
                            dragging = true;
                            v.removeCallbacks(longPressRunnable);
                        }
                        if (dragging) {
                            float maxX = root.getWidth() - v.getWidth();
                            float maxY = root.getHeight() - v.getHeight();
                            v.setTranslationX(Math.max(0, Math.min(maxX, startTx + dx)));
                            v.setTranslationY(Math.max(0, Math.min(maxY, startTy + dy)));
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        v.removeCallbacks(longPressRunnable);
                        v.setAlpha(0.6f);
                        if (dragging) {
                            // Snap to the closest vertical edge
                            float maxX = root.getWidth() - v.getWidth();
                            v.animate().translationX(v.getTranslationX() < maxX / 2 ? dp(8) : maxX - dp(8)).setDuration(150).start();
                        }
                        else if (!longPressed) {
                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                            click.onClick(v);
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        v.removeCallbacks(longPressRunnable);
                        v.setAlpha(0.6f);
                        return true;
                }
                return false;
            }
        });
        return button;
    }

    private void createQuickActionsFab() {
        menuFab = createFloatingButton("\u2630", 80,
                v -> callbacks.onQuickActionsRequested(v), null);
        root.addView(menuFab);
    }

    private TextView makeKey(String label, View.OnClickListener click, View.OnLongClickListener longClick) {
        TextView t = new TextView(context);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(44));
        t.setPadding(dp(10), 0, dp(10), 0);
        t.setBackground(roundRect(COLOR_KEY, 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        t.setLayoutParams(lp);
        t.setHapticFeedbackEnabled(true);
        t.setOnClickListener(click);
        if (longClick != null) {
            t.setOnLongClickListener(longClick);
        }
        return t;
    }

    private void addVk(LinearLayout row, String label, final int vk) {
        row.addView(makeKey(label, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                callbacks.onExtraKey(vk);
            }
        }, null));
    }

    private void addMod(LinearLayout row, String label, final int mod) {
        TextView t = makeKey(label, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if ((locked & mod) != 0) {
                    locked &= ~mod;
                }
                else {
                    latched ^= mod;
                }
                refreshMods();
            }
        }, new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                locked |= mod;
                latched &= ~mod;
                refreshMods();
                return true;
            }
        });
        modViews[mod] = t;
        row.addView(t);
    }

    private void createBar() {
        bar = new HorizontalScrollView(context);
        bar.setHorizontalScrollBarEnabled(false);
        bar.setBackgroundColor(COLOR_BG);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), 0, dp(4), 0);

        row.addView(makeKey("\u2328\u2193", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callbacks.onHideKeyboardRequested();
            }
        }, null));
        addVk(row, "Esc", HostKeyboardLayout.VK_ESCAPE);
        addVk(row, "Tab", HostKeyboardLayout.VK_TAB);
        addMod(row, "Ctrl", MOD_CTRL);
        addMod(row, "Alt", MOD_ALT);
        addMod(row, "AltGr", MOD_ALTGR);
        addMod(row, "Win", MOD_WIN);
        addMod(row, "Maj", MOD_SHIFT);
        addVk(row, "\u2190", HostKeyboardLayout.VK_LEFT);
        addVk(row, "\u2191", HostKeyboardLayout.VK_UP);
        addVk(row, "\u2193", HostKeyboardLayout.VK_DOWN);
        addVk(row, "\u2192", HostKeyboardLayout.VK_RIGHT);
        addVk(row, "Suppr", HostKeyboardLayout.VK_DELETE);
        addVk(row, "Orig", HostKeyboardLayout.VK_HOME);
        addVk(row, "Fin", HostKeyboardLayout.VK_END);
        addVk(row, "PgUp", HostKeyboardLayout.VK_PRIOR);
        addVk(row, "PgDn", HostKeyboardLayout.VK_NEXT);
        addVk(row, "Ins", HostKeyboardLayout.VK_INSERT);
        for (int i = 0; i < 12; i++) {
            addVk(row, "F" + (i + 1), HostKeyboardLayout.VK_F1 + i);
        }
        bar.addView(row);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        bar.setLayoutParams(lp);
        bar.setVisibility(View.GONE);
        root.addView(bar);
    }

    private void refreshMods() {
        for (int mod = 1; mod < modViews.length; mod <<= 1) {
            TextView t = modViews[mod];
            if (t == null) {
                continue;
            }
            int color = (locked & mod) != 0 ? COLOR_LOCKED : (latched & mod) != 0 ? COLOR_LATCHED : COLOR_KEY;
            t.setBackground(roundRect(color, 6));
        }
    }

    /** Current modifiers (one-shot + locked). */
    public int getActiveModifiers() {
        return latched | locked;
    }

    /** Called after a non-modifier key used the modifiers: clears the one-shot ones. */
    public void consumeOneShotModifiers() {
        if (latched != 0) {
            latched = 0;
            refreshMods();
        }
    }

    /** Height of the soft keyboard (0 = hidden). Positions the extra keys bar above it. */
    public void setImeHeight(int imeHeight) {
        if (imeHeight > 0) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bar.getLayoutParams();
            if (lp.bottomMargin != imeHeight) {
                lp.bottomMargin = imeHeight;
                bar.setLayoutParams(lp);
            }
            bar.setVisibility(View.VISIBLE);
            bar.bringToFront();
            if (menuFab != null) {
                menuFab.bringToFront();
            }
        }
        else {
            bar.setVisibility(View.GONE);
            latched = 0;
            locked = 0;
            refreshMods();
        }
    }

    /** Height of the extra keys bar when shown (to keep content visible above it). */
    public int getBarHeight() {
        return bar.getVisibility() == View.VISIBLE ? bar.getHeight() : 0;
    }

    public void setVisible(boolean visible) {
        if (menuFab != null) {
            menuFab.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (!visible) {
            bar.setVisibility(View.GONE);
        }
    }
}
