package com.mxcsyounes.facerecognition2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.camera.core.CameraSelector;
import androidx.core.util.Preconditions;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.ceil;

public class GraphicOverlay extends View {

    private final Object lock = new Object();
    private final List<Graphic> graphics = new ArrayList<>();
    private final Matrix transformationMatrix = new Matrix();
    private int cameraSelector = CameraSelector.LENS_FACING_BACK;
    private int imageWidth;
    private int imageHeight;
    private float scaleFactor = 1.0f;
    private float postScaleWidthOffset;
    private float postScaleHeightOffset;
    private boolean isImageFlipped;
    private boolean needUpdateTransformation = true;


    public GraphicOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        needUpdateTransformation = true);
    }

    public void clear() {
        synchronized (lock) {
            graphics.clear();
        }
        postInvalidate();
    }

    public void add(Graphic graphic) {
        synchronized (lock) {
            graphics.add(graphic);
        }
    }

    public void remove(Graphic graphic) {
        synchronized (lock) {
            graphics.remove(graphic);
        }
        postInvalidate();
    }

    @SuppressLint("RestrictedApi")
    public void setImageSourceInfo(int imageWidth, int imageHeight, boolean isFlipped) {
        Preconditions.checkState(imageWidth > 0, "image width must be positive");
        Preconditions.checkState(imageHeight > 0, "image height must be positive");
        synchronized (lock) {
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.isImageFlipped = isFlipped;
            needUpdateTransformation = true;
        }
        postInvalidate();
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    private void updateTransformationIfNeeded() {
        if (!needUpdateTransformation || imageWidth <= 0 || imageHeight <= 0) {
            return;
        }
        float viewAspectRatio = (float) getWidth() / getHeight();
        float imageAspectRatio = (float) imageWidth / imageHeight;
        postScaleWidthOffset = 0;
        postScaleHeightOffset = 0;
        if (viewAspectRatio > imageAspectRatio) {
            // The image needs to be vertically cropped to be displayed in this view.
            scaleFactor = (float) getWidth() / imageWidth;
            postScaleHeightOffset = ((float) getWidth() / imageAspectRatio - getHeight()) / 2;
        } else {
            // The image needs to be horizontally cropped to be displayed in this view.
            scaleFactor = (float) getHeight() / imageHeight;
            postScaleWidthOffset = ((float) getHeight() * imageAspectRatio - getWidth()) / 2;
        }

        transformationMatrix.reset();
        transformationMatrix.setScale(scaleFactor, scaleFactor);
        transformationMatrix.postTranslate(-postScaleWidthOffset, -postScaleHeightOffset);

        if (isImageFlipped) {
            transformationMatrix.postScale(-1f, 1f, getWidth() / 2f, getHeight() / 2f);
        }

        needUpdateTransformation = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        synchronized (lock) {
            updateTransformationIfNeeded();
            for (Graphic graphic : graphics) {
                graphic.draw(canvas);
            }
        }
    }

    public boolean isFrontMode() {
        return cameraSelector == CameraSelector.LENS_FACING_FRONT;
    }

    public void toggleSelector() {
        if (cameraSelector == CameraSelector.LENS_FACING_BACK)
            cameraSelector = CameraSelector.LENS_FACING_FRONT;
        else cameraSelector = CameraSelector.LENS_FACING_BACK;
    }

    public abstract static class Graphic {
        private final GraphicOverlay overlay;

        public Graphic(GraphicOverlay overlay) {
            this.overlay = overlay;
        }

        public abstract void draw(Canvas canvas);


        public float scale(float imagePixel) {
            return imagePixel * overlay.scaleFactor;
        }

        public Context getApplicationContext() {
            return overlay.getContext().getApplicationContext();
        }

        public boolean isImageFlipped() {
            return overlay.isImageFlipped;
        }

        public float translateX(float x) {
            if (overlay.isImageFlipped) {
                return overlay.getWidth() - (scale(x) - overlay.postScaleWidthOffset);
            } else {
                return scale(x) - overlay.postScaleWidthOffset;
            }
        }


        public float translateY(float y) {
            return scale(y) - overlay.postScaleHeightOffset;
        }


        public Matrix getTransformationMatrix() {
            return overlay.transformationMatrix;
        }

        public void postInvalidate() {
            overlay.postInvalidate();
        }

        public boolean isLandScapeMode() {
            return overlay.getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        }

        @SuppressWarnings("SuspiciousNameCombination")
        public float whenLandScapeModeWidth(float height, float width) {
            if (isLandScapeMode()) return width;
            else return height;
        }

        @SuppressWarnings("SuspiciousNameCombination")
        public float whenLandScapeModeHeight(float height, float width) {
            if (isLandScapeMode()) return height;
            else return width;
        }

        @SuppressWarnings("SuspiciousNameCombination")
        public RectF calculateRect(float height, float width, Rect boundingBoxT) {
            float scaleX = overlay.getWidth() / whenLandScapeModeWidth(height, width);
            float scaleY = overlay.getHeight() / whenLandScapeModeHeight(height, width);
            float scale = Math.max(scaleX, scaleY);
            overlay.scaleFactor = scale;

            // Calculate offset (we need to center the overlay on the target)
            float offsetX = (float) ((overlay.getWidth() - ceil(whenLandScapeModeWidth(height, width) * scale)) / 2.0f);
            float offsetY = (float) ((overlay.getHeight() - ceil(whenLandScapeModeHeight(height, width) * scale)) / 2.0f);

            overlay.postScaleHeightOffset = offsetX;
            overlay.postScaleWidthOffset = offsetY;
            RectF mappedBox = new RectF();

            mappedBox.left = boundingBoxT.right * scale + offsetX;
            mappedBox.top = boundingBoxT.top * scale + offsetY;
            mappedBox.right = boundingBoxT.left * scale + offsetX;
            mappedBox.bottom = boundingBoxT.bottom * scale + offsetY;

            if (overlay.isFrontMode()) {
                float centerX = overlay.getWidth() / 2f;
                mappedBox.left = centerX + (centerX - mappedBox.left);
                mappedBox.right = centerX - (mappedBox.right - centerX);
            }
            return mappedBox;
        }
    }
}