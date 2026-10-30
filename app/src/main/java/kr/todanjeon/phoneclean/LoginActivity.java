package kr.todanjeon.phoneclean;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 로그인 화면. 한국 사용자는 카카오·네이버·구글, 그 외는 구글만(펼치기로 전체 보기). */
public class LoginActivity extends BaseActivity {
    private boolean showAll = false;
    private LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, getString(R.string.login_title), getString(R.string.login_desc)));
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        render();
    }

    private void render() {
        box.removeAllViews();
        boolean kr = Lang.isKoreaUser(this) || showAll;
        if (kr) {
            add(Ui.button(this, getString(R.string.btn_kakao), Color.rgb(254, 229, 0), Color.BLACK), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Auth.kakao(LoginActivity.this, done);
                }
            });
            add(Ui.button(this, getString(R.string.btn_naver), Color.rgb(3, 199, 90), Color.WHITE), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!App.naverReady()) {
                        Ui.toast(LoginActivity.this, getString(R.string.login_unavailable));
                        return;
                    }
                    Auth.naver(LoginActivity.this, done);
                }
            });
        }
        add(Ui.button(this, getString(R.string.btn_google), Color.WHITE, Ui.INK), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Auth.google(LoginActivity.this);
            }
        });
        if (!kr) {
            TextView more = Ui.text(this, getString(R.string.login_more), 14, Ui.GRAY, false);
            more.setPadding(0, Ui.dp(this, 16), 0, 0);
            more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAll = true;
                    render();
                }
            });
            box.addView(more);
        }
        Button lang = Ui.button(this, getString(R.string.btn_language), Ui.NAVY, Color.WHITE);
        add(lang, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, LanguageActivity.class));
                finish();
            }
        });
    }

    private void add(Button bt, View.OnClickListener l) {
        bt.setTextSize(16);
        bt.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        box.addView(bt, lp);
    }

    private final Auth.Done done = new Auth.Done() {
        @Override
        public void ok() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    startActivity(new Intent(LoginActivity.this, FamilyActivity.class));
                    finish();
                }
            });
        }

        @Override
        public void fail(final String msg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Ui.toast(LoginActivity.this, getString(R.string.login_failed, msg));
                }
            });
        }
    };

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == Auth.RC_GOOGLE) Auth.googleResult(this, data, done);
    }
}
