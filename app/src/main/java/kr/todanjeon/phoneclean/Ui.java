package kr.todanjeon.phoneclean;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 화면 만들 때 쓰는 작은 도우미들. */
final class Ui {
    static final int INK = Color.rgb(36, 28, 24);
    static final int CORAL = Color.rgb(255, 99, 58);
    static final int CORAL_DARK = Color.rgb(214, 60, 22);
    static final int NAVY = Color.rgb(32, 40, 66);
    static final int GREEN = Color.rgb(22, 163, 106);
    static final int RED = Color.rgb(214, 40, 40);
    static final int AMBER = Color.rgb(230, 130, 0);
    static final int GRAY = Color.rgb(110, 110, 118);
    static final int BG = Color.rgb(247, 245, 243);

    static final String CAFE_URL = "https://cafe.naver.com/vzk";

    private Ui() {
    }

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static Button button(Context c, String text, int bg, int fg) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(round(bg, 12, c));
        b.setPadding(dp(c, 10), dp(c, 12), dp(c, 10), dp(c, 12));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    static LinearLayout.LayoutParams weight(Context c, int marginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(c, marginDp), dp(c, marginDp), dp(c, marginDp), dp(c, marginDp));
        return lp;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static LinearLayout header(Context c, String title, String sub) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setBackgroundColor(NAVY);
        h.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        TextView t = text(c, title, 20, Color.WHITE, true);
        h.addView(t);
        if (sub != null) {
            TextView s = text(c, sub, 13, Color.rgb(205, 210, 230), false);
            s.setPadding(0, dp(c, 2), 0, 0);
            h.addView(s);
        }
        return h;
    }

    static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_LONG).show();
    }

    static boolean openUrl(Activity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            return true;
        } catch (Exception e) {
            toast(a, "열 수 있는 앱이 없습니다.");
            return false;
        }
    }

    /** 첫 번째 인텐트가 안 열리면 두 번째로 넘어간다. */
    static void openFirst(Activity a, Intent first, Intent fallback) {
        try {
            a.startActivity(first);
        } catch (Exception e1) {
            try {
                if (fallback == null) throw e1;
                a.startActivity(fallback);
            } catch (Exception e2) {
                toast(a, "이 기기에서는 해당 설정 화면을 바로 열 수 없습니다. 설정 앱에서 직접 찾아 주세요.");
            }
        }
    }

    static View space(Context c, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, heightDp)));
        return v;
    }

    static void center(TextView t) {
        t.setGravity(Gravity.CENTER);
    }

    /** 화면이 상태바·내비게이션바 밑까지 확장돼도 내용이 가려지지 않게 여백을 준다. */
    static View fit(View v) {
        v.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public android.view.WindowInsets onApplyWindowInsets(View view, android.view.WindowInsets ins) {
                int l, t, r, b;
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets i = ins.getInsets(android.view.WindowInsets.Type.systemBars()
                            | android.view.WindowInsets.Type.displayCutout());
                    l = i.left; t = i.top; r = i.right; b = i.bottom;
                } else {
                    l = ins.getSystemWindowInsetLeft();
                    t = ins.getSystemWindowInsetTop();
                    r = ins.getSystemWindowInsetRight();
                    b = ins.getSystemWindowInsetBottom();
                }
                view.setPadding(l, t, r, b);
                return android.view.WindowInsets.CONSUMED;
            }
        });
        return v;
    }
}
