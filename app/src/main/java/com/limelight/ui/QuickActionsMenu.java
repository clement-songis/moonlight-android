package com.limelight.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.input.HostKeyboardLayout;

/** A RealVNC-like side panel for Sunshine commands and stream actions. */
public final class QuickActionsMenu {
    public interface Listener {
        void onShortcut(int vk, int modifiers);
        void onToggleKeyboard();
        void onRotate();
        void onResetZoom();
        void onDisconnect();
    }

    private static final int SUNSHINE_MODIFIERS = HostKeyboardLayout.MOD_CTRL |
            HostKeyboardLayout.MOD_ALT | HostKeyboardLayout.MOD_SHIFT;

    private final Context context;
    private final FrameLayout root;
    private final Listener listener;
    private final float density;

    private FrameLayout overlay;
    private LinearLayout panel;
    private View scrim;
    private TextView anchor;
    private boolean fromRight;
    private boolean closing;

    public QuickActionsMenu(Context context, FrameLayout root, Listener listener) {
        this.context = context;
        this.root = root;
        this.listener = listener;
        this.density = context.getResources().getDisplayMetrics().density;
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    public boolean isShowing() {
        return overlay != null;
    }

    public void toggle(View button) {
        if (overlay != null) {
            close();
        }
        else {
            open((TextView) button);
        }
    }

    private void setButtonOpen(TextView button, boolean open) {
        button.setText(open ? "\u00d7" : "\u2630");
        button.setTextSize(open ? 28 : 22);
        button.setContentDescription(context.getString(open ? R.string.quick_close : R.string.quick_actions_title));
    }

    private void open(TextView button) {
        anchor = button;
        fromRight = button.getTranslationX() + button.getWidth() / 2 > root.getWidth() / 2;
        closing = false;

        overlay = new FrameLayout(context);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        scrim = new View(context);
        scrim.setBackgroundColor(Color.BLACK);
        scrim.setAlpha(0);
        scrim.setOnClickListener(v -> close());
        overlay.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = buildPanel();
        int width = Math.min(dp(320), (int) (root.getWidth() * 0.85f));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                width, ViewGroup.LayoutParams.MATCH_PARENT,
                fromRight ? Gravity.END : Gravity.START);
        overlay.addView(panel, panelParams);

        root.addView(overlay);
        // Keep the hamburger/close button above the scrim and the panel.
        anchor.bringToFront();
        setButtonOpen(anchor, true);

        panel.setTranslationX(fromRight ? width : -width);
        panel.animate().translationX(0).setDuration(200)
                .setInterpolator(new DecelerateInterpolator()).start();
        scrim.animate().alpha(0.55f).setDuration(200).start();
    }

    public void close() {
        if (overlay == null || closing) {
            return;
        }
        closing = true;
        final FrameLayout oldOverlay = overlay;
        final TextView oldAnchor = anchor;
        int distance = panel.getWidth() > 0 ? panel.getWidth() : dp(320);
        panel.animate().translationX(fromRight ? distance : -distance).setDuration(160)
                .withEndAction(() -> {
                    root.removeView(oldOverlay);
                    setButtonOpen(oldAnchor, false);
                    if (overlay == oldOverlay) {
                        overlay = null;
                        panel = null;
                        scrim = null;
                        anchor = null;
                    }
                    closing = false;
                }).start();
        scrim.animate().alpha(0).setDuration(160).start();
    }

    private LinearLayout buildPanel() {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xFF17181D);
        panel.setClickable(true);
        panel.setElevation(dp(12));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(22), dp(20), dp(16));
        TextView title = new TextView(context);
        title.setText(R.string.quick_actions_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title);
        TextView subtitle = new TextView(context);
        subtitle.setText(R.string.quick_actions_subtitle);
        subtitle.setTextColor(0xFF9CA3AF);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(3), 0, 0);
        header.addView(subtitle);
        panel.addView(header);

        View divider = new View(context);
        divider.setBackgroundColor(0x26FFFFFF);
        panel.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(10), dp(8), dp(16));
        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        content.addView(makeRow(R.drawable.ic_quick_keyboard, R.string.quick_action_keyboard,
                null, false, v -> runAction(listener::onToggleKeyboard)));
        content.addView(makeRow(R.drawable.ic_quick_rotate, R.string.quick_action_rotate,
                context.getString(R.string.quick_rotate_summary), false,
                v -> runAction(listener::onRotate)));
        content.addView(makeRow(R.drawable.ic_quick_zoom, R.string.quick_action_reset_zoom,
                null, false, v -> runAction(listener::onResetZoom)));

        content.addView(makeSectionLabel(context.getString(R.string.quick_menu_sunshine)));
        content.addView(makeRow(R.drawable.ic_quick_cursor, R.string.quick_sunshine_cursor,
                context.getString(R.string.quick_cursor_shortcut), false,
                v -> runAction(() -> listener.onShortcut('N', SUNSHINE_MODIFIERS))));

        final GridLayout displays = makeDisplayGrid();
        final LinearLayout displayRow = makeRow(R.drawable.ic_quick_monitor,
                R.string.quick_menu_displays, context.getString(R.string.quick_displays_shortcut),
                false, null);
        final ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.ic_quick_chevron);
        chevron.setColorFilter(0xFF9CA3AF);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        chevronParams.setMargins(dp(8), 0, 0, 0);
        displayRow.addView(chevron, chevronParams);
        displayRow.setOnClickListener(v -> {
            boolean expanded = displays.getVisibility() == View.VISIBLE;
            displays.setVisibility(expanded ? View.GONE : View.VISIBLE);
            chevron.animate().rotation(expanded ? 0 : 90).setDuration(150).start();
        });
        content.addView(displayRow);
        content.addView(displays);

        content.addView(makeRow(R.drawable.ic_quick_disconnect, R.string.quick_action_disconnect,
                null, true, v -> runAction(listener::onDisconnect)));
        return panel;
    }

    private TextView makeSectionLabel(String text) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextColor(0xFF8AB4F8);
        label.setTextSize(12);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setPadding(dp(14), dp(22), dp(14), dp(8));
        return label;
    }

    private LinearLayout makeRow(int icon, int title, String summary, boolean destructive,
                                 View.OnClickListener click) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        TypedValue selectable = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectable, true);
        row.setBackgroundResource(selectable.resourceId);
        if (click != null) {
            row.setOnClickListener(click);
        }

        ImageView iconView = new ImageView(context);
        iconView.setImageResource(icon);
        int color = destructive ? 0xFFFF6B6B : 0xFFE5E7EB;
        iconView.setColorFilter(color);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(26), dp(26));
        iconParams.setMargins(dp(2), 0, dp(18), 0);
        row.addView(iconView, iconParams);

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(color);
        titleView.setTextSize(16);
        labels.addView(titleView);
        if (summary != null) {
            TextView summaryView = new TextView(context);
            summaryView.setText(summary);
            summaryView.setTextColor(0xFF9CA3AF);
            summaryView.setTextSize(12);
            summaryView.setPadding(0, dp(2), 0, 0);
            labels.addView(summaryView);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private GridLayout makeDisplayGrid() {
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(2);
        grid.setPadding(dp(4), dp(4), dp(4), dp(8));
        grid.setVisibility(View.GONE);
        for (int display = 1; display <= 12; display++) {
            final int selectedDisplay = display;
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.HORIZONTAL);
            cell.setGravity(Gravity.CENTER_VERTICAL);
            cell.setMinimumHeight(dp(52));
            cell.setPadding(dp(10), dp(6), dp(8), dp(6));
            GradientDrawable background = new GradientDrawable();
            background.setColor(0xFF292B33);
            background.setCornerRadius(dp(8));
            cell.setBackground(background);

            ImageView icon = new ImageView(context);
            icon.setImageResource(R.drawable.ic_quick_monitor);
            icon.setColorFilter(0xFF8AB4F8);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(21), dp(21));
            iconParams.setMargins(0, 0, dp(8), 0);
            cell.addView(icon, iconParams);

            TextView label = new TextView(context);
            label.setText(context.getString(R.string.quick_display_number, display));
            label.setTextColor(Color.WHITE);
            label.setTextSize(14);
            cell.addView(label);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec((display - 1) % 2, 1f);
            params.rowSpec = GridLayout.spec((display - 1) / 2);
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            grid.addView(cell, params);
            cell.setOnClickListener(v -> runAction(() -> listener.onShortcut(
                    HostKeyboardLayout.VK_F1 + selectedDisplay - 1, SUNSHINE_MODIFIERS)));
        }
        return grid;
    }

    private void runAction(Runnable action) {
        close();
        action.run();
    }
}
