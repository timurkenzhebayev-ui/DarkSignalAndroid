package com.example.darksignal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public class HudView extends View {
    private final HorrorRenderer renderer;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int movePointer = -1;
    private int lookPointer = -1;
    private float moveOriginX;
    private float moveOriginY;
    private float moveX;
    private float moveY;
    private float lastLookX;
    private float lastLookY;

    public HudView(Context context, HorrorRenderer renderer) {
        super(context);
        this.renderer = renderer;
        setBackgroundColor(Color.TRANSPARENT);
        setFocusable(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float d = getResources().getDisplayMetrics().density;

        HorrorRenderer.GameState state = renderer.getState();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(115, 0, 0, 0));
        canvas.drawRoundRect(new RectF(14*d, 12*d, 285*d, 56*d), 12*d, 12*d, paint);

        text.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(13*d);
        text.setColor(Color.rgb(218, 238, 242));
        canvas.drawText(renderer.getObjectiveText(), 28*d, 31*d, text);
        text.setTextSize(11*d);
        text.setColor(Color.rgb(112, 211, 214));
        canvas.drawText("ВРЕМЯ " + renderer.getTimeText(), 28*d, 48*d, text);

        if (state == HorrorRenderer.GameState.PLAYING) {
            drawCrosshair(canvas, w * 0.5f, h * 0.5f, d);
            drawJoystick(canvas, d);
            String message = renderer.getTransientMessage();
            if (!message.isEmpty()) drawCenterMessage(canvas, message, h * 0.18f, d);
            if (renderer.isMonsterActive()) {
                text.setTextAlign(Paint.Align.RIGHT);
                text.setTextSize(10.5f*d);
                text.setColor(Color.argb(170, 255, 85, 85));
                canvas.drawText("НЕ ОСТАНАВЛИВАЙСЯ", w - 22*d, 31*d, text);
            }
        } else if (state == HorrorRenderer.GameState.READY) {
            drawDarkOverlay(canvas, 165);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
            text.setTextSize(31*d);
            text.setColor(Color.rgb(224, 244, 245));
            canvas.drawText("ТЁМНЫЙ СИГНАЛ", w*0.5f, h*0.35f, text);

            text.setTextSize(14*d);
            text.setColor(Color.rgb(91, 220, 224));
            canvas.drawText("5-МИНУТНЫЙ 3D-ХОРРОР", w*0.5f, h*0.35f + 28*d, text);

            text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
            text.setTextSize(13*d);
            text.setColor(Color.argb(220, 230, 238, 240));
            canvas.drawText("Левый палец — идти • правый — смотреть", w*0.5f, h*0.61f, text);
            canvas.drawText("Найди 3 предохранителя и вернись к шлюзу", w*0.5f, h*0.61f + 24*d, text);

            text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
            text.setTextSize(16*d);
            text.setColor(Color.WHITE);
            canvas.drawText("ТАПНИ, ЧТОБЫ ВОЙТИ", w*0.5f, h*0.76f, text);
        } else if (state == HorrorRenderer.GameState.GAME_OVER) {
            drawDarkOverlay(canvas, 190);
            drawPanel(canvas, "ОН ТЕБЯ НАШЁЛ", "Предохранители: " + renderer.getFuseCount() + "/3",
                    "ТАПНИ, ЧТОБЫ НАЧАТЬ СНОВА", Color.rgb(220, 60, 65), d);
        } else if (state == HorrorRenderer.GameState.WON) {
            drawDarkOverlay(canvas, 175);
            drawPanel(canvas, "CONGRATULATIONS", "Ты выбрался. Сигнал оборвался.  •  " + renderer.getTimeText(),
                    "ТАПНИ, ЧТОБЫ ПРОЙТИ ЕЩЁ РАЗ", Color.rgb(40, 218, 176), d);
        }

        postInvalidateOnAnimation();
    }

    private void drawPanel(Canvas canvas, String title, String subtitle, String button, int accent, float d) {
        float w = getWidth();
        float h = getHeight();
        float pw = Math.min(w * 0.72f, 620*d);
        float ph = Math.min(h * 0.58f, 250*d);
        float left = (w - pw) * 0.5f;
        float top = (h - ph) * 0.5f;

        paint.setColor(Color.argb(235, 5, 9, 14));
        canvas.drawRoundRect(new RectF(left, top, left + pw, top + ph), 20*d, 20*d, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.3f*d);
        paint.setColor(accent);
        canvas.drawRoundRect(new RectF(left, top, left + pw, top + ph), 20*d, 20*d, paint);
        paint.setStyle(Paint.Style.FILL);

        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        text.setTextSize(25*d);
        text.setColor(Color.WHITE);
        canvas.drawText(title, w*0.5f, top + 60*d, text);

        text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        text.setTextSize(13*d);
        text.setColor(Color.rgb(205, 223, 226));
        canvas.drawText(subtitle, w*0.5f, top + 95*d, text);

        float bl = left + 45*d;
        float br = left + pw - 45*d;
        float bt = top + ph - 82*d;
        float bb = bt + 48*d;
        paint.setColor(accent);
        canvas.drawRoundRect(new RectF(bl, bt, br, bb), 15*d, 15*d, paint);
        text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        text.setTextSize(13*d);
        text.setColor(Color.WHITE);
        canvas.drawText(button, w*0.5f, bt + 30*d, text);
    }

    private void drawDarkOverlay(Canvas canvas, int alpha) {
        paint.setColor(Color.argb(alpha, 0, 0, 0));
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }

    private void drawCenterMessage(Canvas canvas, String message, float y, float d) {
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        text.setTextSize(14*d);
        float tw = text.measureText(message);
        paint.setColor(Color.argb(155, 0, 0, 0));
        canvas.drawRoundRect(new RectF(getWidth()*0.5f - tw*0.5f - 18*d, y - 24*d,
                getWidth()*0.5f + tw*0.5f + 18*d, y + 10*d), 10*d, 10*d, paint);
        text.setColor(Color.rgb(218, 244, 244));
        canvas.drawText(message, getWidth()*0.5f, y, text);
    }

    private void drawCrosshair(Canvas canvas, float cx, float cy, float d) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.1f*d);
        paint.setColor(Color.argb(125, 190, 239, 241));
        canvas.drawCircle(cx, cy, 4*d, paint);
        canvas.drawLine(cx - 11*d, cy, cx - 6*d, cy, paint);
        canvas.drawLine(cx + 6*d, cy, cx + 11*d, cy, paint);
        canvas.drawLine(cx, cy - 11*d, cx, cy - 6*d, paint);
        canvas.drawLine(cx, cy + 6*d, cx, cy + 11*d, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawJoystick(Canvas canvas, float d) {
        float radius = 52*d;
        float cx = movePointer >= 0 ? moveOriginX : 72*d;
        float cy = movePointer >= 0 ? moveOriginY : getHeight() - 72*d;
        paint.setColor(Color.argb(38, 190, 239, 241));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f*d);
        paint.setColor(Color.argb(82, 190, 239, 241));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.FILL);

        float knobX = movePointer >= 0 ? moveX : cx;
        float knobY = movePointer >= 0 ? moveY : cy;
        paint.setColor(Color.argb(105, 84, 224, 226));
        canvas.drawCircle(knobX, knobY, 22*d, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        HorrorRenderer.GameState state = renderer.getState();
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        if ((state == HorrorRenderer.GameState.READY ||
                state == HorrorRenderer.GameState.GAME_OVER ||
                state == HorrorRenderer.GameState.WON) &&
                action == MotionEvent.ACTION_DOWN) {
            if (state == HorrorRenderer.GameState.READY) renderer.startGame();
            else renderer.restartAndPlay();
            invalidate();
            return true;
        }

        if (state != HorrorRenderer.GameState.PLAYING) return true;

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int id = event.getPointerId(actionIndex);
            float x = event.getX(actionIndex);
            float y = event.getY(actionIndex);
            if (x < getWidth() * 0.48f && movePointer < 0) {
                movePointer = id;
                moveOriginX = x;
                moveOriginY = y;
                moveX = x;
                moveY = y;
                updateMove(x, y);
            } else if (lookPointer < 0) {
                lookPointer = id;
                lastLookX = x;
                lastLookY = y;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int id = event.getPointerId(i);
                float x = event.getX(i);
                float y = event.getY(i);
                if (id == movePointer) updateMove(x, y);
                if (id == lookPointer) {
                    renderer.addLook(x - lastLookX, y - lastLookY);
                    lastLookX = x;
                    lastLookY = y;
                }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            int id = event.getPointerId(actionIndex);
            if (id == movePointer || action == MotionEvent.ACTION_CANCEL) {
                movePointer = -1;
                renderer.setMove(0f, 0f);
            }
            if (id == lookPointer || action == MotionEvent.ACTION_CANCEL) {
                lookPointer = -1;
            }
        }
        invalidate();
        return true;
    }

    private void updateMove(float x, float y) {
        float d = getResources().getDisplayMetrics().density;
        float radius = 52*d;
        float dx = x - moveOriginX;
        float dy = y - moveOriginY;
        float len = (float) Math.sqrt(dx*dx + dy*dy);
        if (len > radius) {
            dx = dx / len * radius;
            dy = dy / len * radius;
        }
        moveX = moveOriginX + dx;
        moveY = moveOriginY + dy;
        renderer.setMove(dx / radius, -dy / radius);
    }
}
