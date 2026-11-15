package com.limelight.ui;

import android.graphics.Matrix;
import android.view.MotionEvent;
import android.view.View;

/**
 * Pinch-to-zoom and pan of the stream, RealVNC style.
 *
 * The StreamView is scaled visually (pivot at its top-left corner) and translated. Touch events
 * arriving on the full-screen background view are mapped back to "unzoomed" coordinates so the
 * rest of Moonlight's input code (absolute and trackpad modes) keeps working unchanged.
 *
 * Two-finger gesture classification:
 *  - distance between fingers changes more than the slop -> ZOOM (zoom around the fingers + pan)
 *  - zoomed in and fingers move together -> PAN (unless the user prefers scrolling)
 *  - otherwise -> left to Moonlight (two-finger scroll / right click)
 */
public class ZoomController {
    public interface Listener {
        /** Called when a gesture is taken over by the zoom: pending touches must be cancelled. */
        void onZoomGestureStarted();
        void onZoomChanged(float scale);
    }

    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 8.0f;

    private enum State { IDLE, PENDING, ZOOM, PAN, CONSUME_UNTIL_UP }

    private final View target;
    private final View container;
    private final Listener listener;
    private final float pinchSlop;
    private final float panSlop;
    private final boolean twoFingerPanWhenZoomed;

    private float scale = 1.0f;
    private float transX = 0, transY = 0;
    private int bottomInset = 0;

    private State state = State.IDLE;
    private float startDist, startScale;
    private float startFocusX, startFocusY;
    // Content point (in unscaled target coordinates) under the fingers at gesture start
    private float anchorX, anchorY;
    private float lastFocusX, lastFocusY;

    private final Matrix inverse = new Matrix();

    public ZoomController(View target, View container, Listener listener, boolean twoFingerPanWhenZoomed) {
        this.target = target;
        this.container = container;
        this.listener = listener;
        this.twoFingerPanWhenZoomed = twoFingerPanWhenZoomed;
        float density = target.getResources().getDisplayMetrics().density;
        this.pinchSlop = 28 * density;
        this.panSlop = 16 * density;
        target.setPivotX(0);
        target.setPivotY(0);
    }

    public float getScale() {
        return scale;
    }

    public boolean isZoomed() {
        return scale > 1.001f;
    }

    /** Visible area shrinks when the soft keyboard is shown: allow panning above it. */
    public void setBottomInset(int inset) {
        if (inset == bottomInset) {
            return;
        }
        bottomInset = inset;
        if (!isZoomed()) {
            // Unzoomed: re-center, then apply() lifts the content above the keyboard if needed
            transX = transY = 0;
        }
        apply();
    }

    public int getBottomInset() {
        return bottomInset;
    }

    public void reset() {
        scale = 1.0f;
        transX = transY = 0;
        apply();
    }

    /** Make sure a point of the container (e.g. last touch) is visible above the keyboard. */
    public void ensureVisible(float containerX, float containerY) {
        float visibleBottom = container.getHeight() - bottomInset;
        float margin = visibleBottom * 0.2f;
        if (containerY > visibleBottom - margin) {
            transY -= containerY - (visibleBottom - margin);
            apply();
        }
    }

    private static float dist(MotionEvent e) {
        float dx = e.getX(0) - e.getX(1);
        float dy = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float focusX(MotionEvent e) {
        return (e.getX(0) + e.getX(1)) / 2;
    }

    private static float focusY(MotionEvent e) {
        return (e.getY(0) + e.getY(1)) / 2;
    }

    /**
     * Feed a finger touch event (coordinates relative to the container).
     * @return true if the event belongs to a zoom/pan gesture and must not reach Moonlight.
     */
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                state = State.IDLE;
                return false;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (state == State.ZOOM || state == State.PAN || state == State.CONSUME_UNTIL_UP) {
                    // Extra finger during a zoom: keep eating everything
                    state = State.CONSUME_UNTIL_UP;
                    return true;
                }
                if (e.getPointerCount() == 2) {
                    state = State.PENDING;
                    startDist = Math.max(dist(e), 1);
                    startScale = scale;
                    startFocusX = lastFocusX = focusX(e);
                    startFocusY = lastFocusY = focusY(e);
                    anchorX = (startFocusX - target.getLeft() - transX) / scale;
                    anchorY = (startFocusY - target.getTop() - transY) / scale;
                }
                else {
                    // 3+ fingers: not ours (keyboard gesture)
                    state = State.IDLE;
                }
                return false;

            case MotionEvent.ACTION_MOVE:
                if (e.getPointerCount() < 2) {
                    return state == State.CONSUME_UNTIL_UP;
                }
                if (state == State.PENDING) {
                    float d = dist(e);
                    float fx = focusX(e), fy = focusY(e);
                    float moved = (float) Math.hypot(fx - startFocusX, fy - startFocusY);
                    if (Math.abs(d - startDist) > pinchSlop) {
                        state = State.ZOOM;
                        // Restart from here so the zoom does not jump by the slop
                        startDist = d;
                        startScale = scale;
                        listener.onZoomGestureStarted();
                    }
                    else if (twoFingerPanWhenZoomed && isZoomed() && moved > panSlop) {
                        state = State.PAN;
                        lastFocusX = fx;
                        lastFocusY = fy;
                        listener.onZoomGestureStarted();
                        return true;
                    }
                    else {
                        return false;
                    }
                }
                if (state == State.ZOOM) {
                    float d = dist(e);
                    float fx = focusX(e), fy = focusY(e);
                    scale = clamp(startScale * d / startDist, MIN_SCALE, MAX_SCALE);
                    // Keep the anchored content point under the fingers' focus
                    transX = fx - target.getLeft() - anchorX * scale;
                    transY = fy - target.getTop() - anchorY * scale;
                    apply();
                    listener.onZoomChanged(scale);
                    return true;
                }
                if (state == State.PAN) {
                    float fx = focusX(e), fy = focusY(e);
                    transX += fx - lastFocusX;
                    transY += fy - lastFocusY;
                    lastFocusX = fx;
                    lastFocusY = fy;
                    apply();
                    return true;
                }
                return state == State.CONSUME_UNTIL_UP;

            case MotionEvent.ACTION_POINTER_UP:
                if (state == State.ZOOM || state == State.PAN) {
                    state = State.CONSUME_UNTIL_UP;
                    return true;
                }
                if (state == State.PENDING) {
                    state = State.IDLE;
                }
                return state == State.CONSUME_UNTIL_UP;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean consumed = state == State.ZOOM || state == State.PAN || state == State.CONSUME_UNTIL_UP;
                state = State.IDLE;
                return consumed;
            }
        }
        return false;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // Allowed translation for one axis so the content covers the visible area when possible
    private static float clampAxis(float t, float base, float length, float vMin, float vMax) {
        float lo, hi;
        if (length >= vMax - vMin) {
            lo = vMax - base - length;
            hi = vMin - base;
        }
        else {
            // Content smaller than the visible area: keep it fully inside it (this lifts a
            // letterboxed portrait stream above the soft keyboard)
            lo = vMin - base;
            hi = vMax - base - length;
        }
        return clamp(t, lo, hi);
    }

    private void apply() {
        if (container.getWidth() == 0 || target.getWidth() == 0) {
            return;
        }
        float w = target.getWidth() * scale;
        float h = target.getHeight() * scale;
        transX = clampAxis(transX, target.getLeft(), w, 0, container.getWidth());
        transY = clampAxis(transY, target.getTop(), h, 0, container.getHeight() - bottomInset);
        target.setScaleX(scale);
        target.setScaleY(scale);
        target.setTranslationX(transX);
        target.setTranslationY(transY);

        // Container coordinates -> unzoomed container coordinates, where Moonlight expects
        // target.getX() == left + translationX and an unscaled view:
        // p = (p' - origin) / scale + origin, with origin = left + transX
        float ox = target.getLeft() + transX;
        float oy = target.getTop() + transY;
        inverse.reset();
        inverse.postTranslate(-ox, -oy);
        inverse.postScale(1 / scale, 1 / scale);
        inverse.postTranslate(ox, oy);
    }

    /** Re-apply constraints after a layout change (rotation, resize). */
    public void onLayoutChanged() {
        apply();
    }

    /** Map a touch event from container coordinates to what Moonlight expects. In place. */
    public void mapEvent(MotionEvent e) {
        if (isZoomed() || transX != 0 || transY != 0) {
            e.transform(inverse);
        }
    }
}
