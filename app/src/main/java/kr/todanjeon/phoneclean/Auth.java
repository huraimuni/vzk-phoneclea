package kr.todanjeon.phoneclean;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.functions.FirebaseFunctions;
import com.kakao.sdk.auth.model.OAuthToken;
import com.kakao.sdk.user.UserApiClient;
import com.navercorp.nid.NaverIdLoginSDK;
import com.navercorp.nid.oauth.OAuthLoginCallback;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;

/** 로그인 모음: 구글(Firebase) / 카카오·네이버(서버 확인 후 Firebase 맞춤 토큰). */
final class Auth {
    static final int RC_GOOGLE = 9001;
    static final String REGION = "asia-northeast3";

    interface Done {
        void ok();

        void fail(String msg);
    }

    private Auth() {
    }

    static FirebaseUser user() {
        try {
            return FirebaseAuth.getInstance().getCurrentUser();
        } catch (Throwable t) {
            return null;
        }
    }

    static String uid() {
        FirebaseUser u = user();
        return u == null ? null : u.getUid();
    }

    static String myName(Context c) {
        String n = c.getSharedPreferences("account", Context.MODE_PRIVATE).getString("name", null);
        if (n != null && !n.isEmpty()) return n;
        FirebaseUser u = user();
        if (u != null && u.getDisplayName() != null && !u.getDisplayName().isEmpty()) return u.getDisplayName();
        if (u != null && u.getEmail() != null) return u.getEmail();
        return "VZK";
    }

    static void signOut(Context c) {
        try {
            FirebaseAuth.getInstance().signOut();
            GoogleSignIn.getClient(c, new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()).signOut();
        } catch (Throwable ignored) {
        }
        c.getSharedPreferences("account", Context.MODE_PRIVATE).edit().clear().apply();
    }

    // ------------------------------------------------------------ Google
    static void google(Activity a) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(a.getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        a.startActivityForResult(GoogleSignIn.getClient(a, gso).getSignInIntent(), RC_GOOGLE);
    }

    static void googleResult(final Context c, Intent data, final Done d) {
        try {
            final GoogleSignInAccount acc = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
            AuthCredential cr = GoogleAuthProvider.getCredential(acc.getIdToken(), null);
            FirebaseAuth.getInstance().signInWithCredential(cr).addOnCompleteListener(t -> {
                if (t.isSuccessful()) {
                    String name = acc.getDisplayName() != null ? acc.getDisplayName() : acc.getEmail();
                    saveProfile(c, name, "google", d);
                } else {
                    d.fail(msg(t.getException()));
                }
            });
        } catch (ApiException e) {
            d.fail("Google " + e.getStatusCode());
        }
    }

    // ------------------------------------------------------------ Kakao
    static void kakao(final Activity a, final Done d) {
        Function2<OAuthToken, Throwable, Unit> cb = (token, err) -> {
            if (err != null) d.fail(msg(err));
            else if (token != null) exchange(a, "kakaoLogin", token.getAccessToken(), "kakao", d);
            return Unit.INSTANCE;
        };
        UserApiClient client = UserApiClient.getInstance();
        if (client.isKakaoTalkLoginAvailable(a)) client.loginWithKakaoTalk(a, cb);
        else client.loginWithKakaoAccount(a, cb);
    }

    // ------------------------------------------------------------ Naver
    static void naver(final Activity a, final Done d) {
        NaverIdLoginSDK.INSTANCE.authenticate(a, new OAuthLoginCallback() {
            @Override
            public void onSuccess() {
                exchange(a, "naverLogin", NaverIdLoginSDK.INSTANCE.getAccessToken(), "naver", d);
            }

            @Override
            public void onFailure(int httpStatus, String message) {
                d.fail(message);
            }

            @Override
            public void onError(int errorCode, String message) {
                d.fail(message);
            }
        });
    }

    // ------------------------------------------------------------ 공통
    @SuppressWarnings("unchecked")
    private static void exchange(final Context c, String fn, String accessToken, final String provider, final Done d) {
        if (accessToken == null) {
            d.fail("token");
            return;
        }
        FirebaseFunctions.getInstanceFor(REGION).getHttpsCallable(fn)
                .call(Collections.singletonMap("accessToken", accessToken))
                .addOnSuccessListener(r -> {
                    Map<String, Object> m = (Map<String, Object>) r.getData();
                    final String ct = (String) m.get("token");
                    final String name = (String) m.get("name");
                    FirebaseAuth.getInstance().signInWithCustomToken(ct).addOnCompleteListener(t -> {
                        if (t.isSuccessful()) saveProfile(c, name, provider, d);
                        else d.fail(msg(t.getException()));
                    });
                })
                .addOnFailureListener(e -> d.fail(msg(e)));
    }

    private static void saveProfile(final Context c, String name, String provider, final Done d) {
        if (name == null || name.isEmpty()) name = "VZK";
        c.getSharedPreferences("account", Context.MODE_PRIVATE).edit()
                .putString("name", name).putString("provider", provider).apply();
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("provider", provider);
        m.put("lang", Lang.tag(c));
        m.put("updatedAt", com.google.firebase.Timestamp.now());
        FirebaseFirestore.getInstance().collection("users").document(uid())
                .set(m, SetOptions.merge())
                .addOnCompleteListener(t -> d.ok());
    }

    static String msg(Throwable e) {
        return e == null ? "?" : (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
}
