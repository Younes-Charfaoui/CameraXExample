package com.mxcsyounes.facerecognition2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

public class BoundingBoxContourGraphic extends GraphicOverlay.Graphic {

    private final Rect objectBoundingBox;
    private final Rect imageRect;
    private final Paint boxPaint;

    public BoundingBoxContourGraphic(GraphicOverlay overlay, Rect objectBoundingBox, Rect imageRect) {
        super(overlay);
        this.objectBoundingBox = objectBoundingBox;
        this.imageRect = imageRect;
        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5.0f);
    }

    @Override
    public void draw(Canvas canvas) {
        RectF rectF = calculateRect(imageRect.height(), imageRect.width(), objectBoundingBox);
        //RectF rectF = new RectF(objectBoundingBox);
        canvas.drawRect(rectF, boxPaint);
    }
}