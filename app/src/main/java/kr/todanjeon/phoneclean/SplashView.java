package kr.todanjeon.phoneclean;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** 시작할 때 잠깐 보이는 VZK 로고 화면 (그림 파일 없이 직접 그림). */
class SplashView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int CORAL = Color.rgb(255, 99, 58);
    private final int NAVY = Color.rgb(32, 40, 66);

    SplashView(Context c) {
        super(c);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStyle(Paint.Style.STROKE);
        setClickable(true); // 뒤 화면이 눌리지 않게
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2, float w, int col) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setColor(col);
        c.drawLine(x1, y1, x2, y2, p);
    }

    @Override
    protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        c.drawColor(Color.WHITE);
        float s = Math.min(W, H) * 0.46f;
        float cx = W / 2f, cy = H * 0.42f;
        // 둥근 사각형 로고판
        p.setStyle(Paint.Style.FILL);
        p.setColor(CORAL);
        c.drawRoundRect(new RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2), s * 0.22f, s * 0.22f, p);
        // VZK 글자 (기존 로고와 같은 모양)
        float h = s * 0.32f, w = s * 0.06f;
        float lw = h * 0.60f, g = h * 0.30f, total = 3 * lw + 2 * g;
        float x0 = cx - total / 2, y0 = cy - h / 2, x1 = x0 + lw + g, x2 = x1 + lw + g;
        int white = Color.WHITE;
        line(c, x0, y0, x0 + lw / 2, y0 + h, w, white);
        line(c, x0 + lw / 2, y0 + h, x0 + lw, y0, w, white);
        line(c, x1, y0, x1 + lw, y0, w, white);
        line(c, x1, y0 + h, x1 + lw, y0 + h, w, white);
        line(c, x1 + lw, y0, x1, y0 + h, w, NAVY);
        line(c, x2, y0, x2, y0 + h, w, white);
        line(c, x2 + lw, y0, x2 + lw * 0.02f, y0 + h * 0.58f, w, white);
        line(c, x2 + lw * 0.28f, y0 + h * 0.44f, x2 + lw, y0 + h, w, white);
        // 이름
        p.setStyle(Paint.Style.FILL);
        p.setColor(NAVY);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(s * 0.15f);
        c.drawText("VZK 폰클린", cx, cy + s / 2 + s * 0.26f, p);
        p.setFakeBoldText(false);
        p.setColor(Color.rgb(110, 110, 118));
        p.setTextSize(s * 0.075f);
        c.drawText("정크 앱 정리 도우미", cx, cy + s / 2 + s * 0.40f, p);
    }
}
