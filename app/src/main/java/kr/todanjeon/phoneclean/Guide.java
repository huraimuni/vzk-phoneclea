package kr.todanjeon.phoneclean;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 도와주는 사람이 보낸 안내(동그라미·말풍선)를 도움받는 사람 화면 위에 잠깐 그린다.
 * 터치를 가로채지 않고(NOT_TOUCHABLE), 화면을 대신 조작하지도 않는다. 보여 주기만 한다.
 */
final class Guide {
    private final Context c;
    private final WindowManager wm;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Pulse pulse;
    private TextView bubble;

    Guide(Context c) {
        this.c = c;
        this.wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
    }

    static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    private WindowManager.LayoutParams params(int gravity) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = gravity;
        return lp;
    }

    /** 화면 비율 좌표(0~1)에 3초간 동그라미 */
    void circle(final float rx, final float ry) {
        if (!allowed(c)) return;
        h.post(new Runnable() {
            @Override
            public void run() {
                try {
                    remove(pulse);
                    pulse = new Pulse(c, rx, ry);
                    wm.addView(pulse, params(Gravity.TOP | Gravity.START));
                    final Pulse mine = pulse;
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            remove(mine);
                            if (pulse == mine) pulse = null;
                        }
                    }, 3000);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** 미리 정해 둔 안내 문구를 화면 아래쪽에 큰 글씨로 5초간 */
    void say(final String text) {
        if (!allowed(c)) return;
        h.post(new Runnable() {
            @Override
            public void run() {
                try {
                    remove(bubble);
                    TextView t = new TextView(c);
                    t.setText("💬 " + text);
                    t.setTextSize(24);
                    t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    t.setTextColor(Color.WHITE);
                    t.setGravity(Gravity.CENTER);
                    t.setBackground(Ui.round(Color.argb(235, 32, 40, 66), 18, c));
                    t.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 14));
                    bubble = t;
                    WindowManager.LayoutParams lp = params(Gravity.BOTTOM);
                    lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
                    lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                    lp.y = Ui.dp(c, 90);
                    lp.horizontalMargin = 0.04f;
                    wm.addView(t, lp);
                    final TextView mine = t;
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            remove(mine);
                            if (bubble == mine) bubble = null;
                        }
                    }, 5000);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    void clear() {
        h.removeCallbacksAndMessages(null);
        remove(pulse);
        remove(bubble);
        pulse = null;
        bubble = null;
    }

    private void remove(View v) {
        if (v == null) return;
        try {
            wm.removeView(v);
        } catch (Throwable ignored) {
        }
    }

    private static final class Pulse extends View {
        private final float rx, ry;
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long t0 = System.currentTimeMillis();

        Pulse(Context c, float rx, float ry) {
            super(c);
            this.rx = rx;
            this.ry = ry;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Ui.dp(c, 6));
            ring.setColor(Color.rgb(255, 60, 40));
            dot.setColor(Color.argb(120, 255, 60, 40));
        }

        @Override
        protected void onDraw(Canvas cv) {
            float x = rx * getWidth(), y = ry * getHeight();
            float k = ((System.currentTimeMillis() - t0) % 900) / 900f;
            float base = Ui.dp(getContext(), 34);
            cv.drawCircle(x, y, base, dot);
            cv.drawCircle(x, y, base + k * Ui.dp(getContext(), 22), ring);
            postInvalidateOnAnimation();
        }
    }
}
