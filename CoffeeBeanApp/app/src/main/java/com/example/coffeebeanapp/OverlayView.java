package com.example.coffeebeanapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import org.tensorflow.lite.task.vision.classifier.Classifications;

import java.util.List;
import java.util.Locale;

public class OverlayView extends View {
    private String classificationText = "";
    private Paint textBackgroundPaint;
    private Paint textPaint;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }


    private void initPaints() {
        textBackgroundPaint = new Paint();
        textBackgroundPaint.setColor(Color.argb(150, 0, 0, 0));
        textBackgroundPaint.setStyle(Paint.Style.FILL);
        textBackgroundPaint.setTextSize(30f);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(30f);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setClassificationResult(String resultText) {
        this.classificationText = resultText != null ? resultText : "";
        invalidate();
    }

    public void clear() {
        this.classificationText = "";
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (classificationText != null && !classificationText.isEmpty()) {
            float xPos = getWidth() / 2f;
            float yPos = (getHeight() / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f);

            float textWidth = textPaint.measureText(classificationText);
            float textHeight = textPaint.descent() - textPaint.ascent();
            float padding = 20f;

            canvas.drawRect(xPos - textWidth / 2 - padding,
                    yPos - textHeight - padding,
                    xPos + textWidth / 2 + padding,
                    yPos + padding,
                    textBackgroundPaint);

            canvas.drawText(classificationText, xPos, yPos, textPaint);
        }
    }
}