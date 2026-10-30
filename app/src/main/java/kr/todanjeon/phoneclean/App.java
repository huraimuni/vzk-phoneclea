package kr.todanjeon.phoneclean;

import android.app.Application;

import com.kakao.sdk.common.KakaoSdk;
import com.navercorp.nid.NaverIdLoginSDK;

public class App extends Application {
    static final String KAKAO_NATIVE_KEY = "f8898377c9756b28f0d527714d44467e";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            KakaoSdk.init(this, KAKAO_NATIVE_KEY);
        } catch (Throwable ignored) {
        }
        if (naverReady()) {
            try {
                NaverIdLoginSDK.INSTANCE.initialize(this, BuildConfig.NAVER_CLIENT_ID,
                        BuildConfig.NAVER_CLIENT_SECRET, "VZK 폰클린");
            } catch (Throwable ignored) {
            }
        }
    }

    static boolean naverReady() {
        return !BuildConfig.NAVER_CLIENT_ID.isEmpty() && !BuildConfig.NAVER_CLIENT_SECRET.isEmpty();
    }
}
