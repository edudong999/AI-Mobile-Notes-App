package com.ai_photo.ui.common;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/** 简易支持 pinch-zoom + pan 的 ImageView。无 fling/inertia，但够用。 */
public class TouchImageView extends AppCompatImageView {
    private final Matrix matrix0 = new Matrix();
    private final Matrix matrix1 = new Matrix();
    private final float[] values = new float[9];

    private PointF last = new PointF();
    private PointF start = new PointF();
    private float minScale = 1f;
    private float maxScale = 5f;
    private int mode = NONE;
    private float lastDist;
    private static final int NONE = 0, DRAG = 1, ZOOM = 2;

    public TouchImageView(Context context) { super(context); init(); }
    public TouchImageView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public TouchImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); init();
    }

    private void init() {
        setScaleType(ScaleType.MATRIX);
        super.setImageMatrix(matrix0);
    }

    @Override public void setImageMatrix(Matrix m) {
        super.setImageMatrix(m);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (getDrawable() != null) {
            int iw = getDrawable().getIntrinsicWidth();
            int ih = getDrawable().getIntrinsicHeight();
            int vw = getWidth();
            int vh = getHeight();
            if (iw > 0 && ih > 0 && vw > 0 && vh > 0) {
                float scale = Math.min((float) vw / iw, (float) vh / ih);
                matrix0.reset();
                matrix0.postScale(scale, scale);
                matrix0.postTranslate((vw - iw * scale) / 2f, (vh - ih * scale) / 2f);
                super.setImageMatrix(matrix0);
                matrix1.set(matrix0);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                last.set(e.getX(), e.getY());
                start.set(last.x, last.y);
                mode = DRAG;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                lastDist = spacing(e);
                if (lastDist > 10f) mode = ZOOM;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG) {
                    float dx = e.getX() - last.x;
                    float dy = e.getY() - last.y;
                    matrix1.postTranslate(dx, dy);
                    last.set(e.getX(), e.getY());
                } else if (mode == ZOOM && e.getPointerCount() >= 2) {
                    float newDist = spacing(e);
                    float scale = newDist / lastDist;
                    matrix1.postScale(scale, scale, e.getX(0) / 2f + e.getX(1) / 2f,
                        e.getY(0) / 2f + e.getY(1) / 2f);
                    lastDist = newDist;
                }
                applyBounds();
                super.setImageMatrix(matrix1);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                mode = NONE;
                break;
        }
        return true;
    }

    private float spacing(MotionEvent e) {
        if (e.getPointerCount() < 2) return 0f;
        float x = e.getX(0) - e.getX(1);
        float y = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void applyBounds() {
        matrix1.getValues(values);
        float scale = values[Matrix.MSCALE_X];
        if (scale < minScale) {
            float factor = minScale / scale;
            matrix1.postScale(factor, factor, getWidth() / 2f, getHeight() / 2f);
        } else if (scale > maxScale) {
            float factor = maxScale / scale;
            matrix1.postScale(factor, factor, getWidth() / 2f, getHeight() / 2f);
        }
    }
}