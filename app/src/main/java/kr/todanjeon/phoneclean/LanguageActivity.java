package kr.todanjeon.phoneclean;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** 첫 실행 때와 설정에서 쓰는 언어 선택 화면. */
public class LanguageActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "🌐 " + getString(R.string.lang_title), getString(R.string.lang_hint)));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        String cur = Lang.tag(this);
        for (int i = 0; i < Lang.TAGS.length; i++) {
            final String tag = Lang.TAGS[i];
            boolean sel = tag.equals(cur) && Lang.isChosen(this);
            Button bt = Ui.button(this, (sel ? "✓ " : "") + Lang.NAMES[i], sel ? Ui.CORAL : Color.WHITE, sel ? Color.WHITE : Ui.INK);
            bt.setTextSize(17);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Lang.set(LanguageActivity.this, tag);
                    Intent it = new Intent(LanguageActivity.this, MainActivity.class);
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(it);
                    finish();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
            list.addView(bt, lp);
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override
    public void onBackPressed() {
        if (Lang.isChosen(this)) super.onBackPressed();
    }
}
