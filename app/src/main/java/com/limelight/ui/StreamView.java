package com.limelight.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class StreamView extends SurfaceView {
    private double desiredAspectRatio;
    private InputCallbacks inputCallbacks;

    public void setDesiredAspectRatio(double aspectRatio) {
        this.desiredAspectRatio = aspectRatio;
    }

    public void setInputCallbacks(InputCallbacks callbacks) {
        this.inputCallbacks = callbacks;
    }

    public StreamView(Context context) {
        super(context);
    }

    public StreamView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StreamView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public StreamView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // If no fixed aspect ratio has been provided, simply use the default onMeasure() behavior
        if (desiredAspectRatio == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // Based on code from: https://www.buzzingandroid.com/2012/11/easy-measuring-of-custom-views-with-specific-aspect-ratio/
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int measuredHeight, measuredWidth;
        if (widthSize > heightSize * desiredAspectRatio) {
            measuredHeight = heightSize;
            measuredWidth = (int)(measuredHeight * desiredAspectRatio);
        } else {
            measuredWidth = widthSize;
            measuredHeight = (int)(measuredWidth / desiredAspectRatio);
        }

        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        // This callbacks allows us to override dumb IME behavior like when
        // Samsung's default keyboard consumes Shift+Space.
        if (inputCallbacks != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (inputCallbacks.handleKeyDown(event)) {
                    return true;
                }
            }
            else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (inputCallbacks.handleKeyUp(event)) {
                    return true;
                }
            }
        }

        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        // Required for the IME to reliably attach to us (show/hide on demand),
        // instead of the deprecated toggleSoftInput() that silently fails on
        // non-editor views on recent Android versions.
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        // TYPE_NULL puts the IME in "raw keys" mode (like a terminal emulator): no
        // autocorrect, no suggestions, keys are delivered as KeyEvents. IMEs that still
        // commit text or compose are handled by StreamInputConnection.
        outAttrs.inputType = EditorInfo.TYPE_NULL;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI |
                EditorInfo.IME_FLAG_NO_FULLSCREEN |
                EditorInfo.IME_ACTION_NONE;
        return new StreamInputConnection(this);
    }

    private class StreamInputConnection extends BaseInputConnection {
        // Text currently "composing" in the IME that we already typed on the host
        private String composing = "";

        StreamInputConnection(StreamView view) {
            super(view, false);
        }

        private boolean sendText(CharSequence text) {
            if (inputCallbacks == null || text == null || text.length() == 0) {
                return false;
            }
            inputCallbacks.handleText(text.toString());
            return true;
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            String t = text == null ? "" : text.toString();
            if (!composing.isEmpty()) {
                // Replace what the composing region already typed
                applyComposingDiff(t);
                composing = "";
                return true;
            }
            if (!sendText(t)) {
                return super.commitText(text, newCursorPosition);
            }
            return true;
        }

        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            applyComposingDiff(text == null ? "" : text.toString());
            return true;
        }

        @Override
        public boolean finishComposingText() {
            composing = "";
            return true;
        }

        private void applyComposingDiff(String next) {
            if (inputCallbacks == null) {
                composing = next;
                return;
            }
            int common = 0;
            int max = Math.min(composing.length(), next.length());
            while (common < max && composing.charAt(common) == next.charAt(common)) {
                common++;
            }
            int toDelete = composing.length() - common;
            if (toDelete > 0) {
                inputCallbacks.handleDeleteBackward(toDelete);
            }
            if (common < next.length()) {
                inputCallbacks.handleText(next.substring(common));
            }
            composing = next;
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            if (inputCallbacks != null && beforeLength > 0) {
                inputCallbacks.handleDeleteBackward(beforeLength);
                return true;
            }
            return super.deleteSurroundingText(beforeLength, afterLength);
        }
    }

    public interface InputCallbacks {
        boolean handleKeyUp(KeyEvent event);
        boolean handleKeyDown(KeyEvent event);
        void handleText(String text);
        void handleDeleteBackward(int count);
    }
}
