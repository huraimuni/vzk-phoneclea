package kr.todanjeon.phoneclean;

import android.app.Activity;
import android.content.Context;

/** 모든 화면이 앱 언어 설정을 따르도록 하는 기본 화면. */
public class BaseActivity extends Activity {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }
}
