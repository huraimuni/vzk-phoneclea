package kr.todanjeon.phoneclean;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 가족 연결 화면.
 * 도움받는 사람(부모)이 초대 코드를 만들고, 도와주는 사람(자녀)이 코드를 입력/스캔해 요청 → 부모가 수락해야 연결된다.
 * 연결 추가·해제·차단은 부모만 할 수 있고(규칙으로 강제), 자녀는 '해제 요청'만 보낼 수 있다.
 */
public class FamilyActivity extends BaseActivity {
    static final int INVITE_MINUTES = 10;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<DocumentSnapshot> asParent = new ArrayList<>();
    private final List<DocumentSnapshot> asChild = new ArrayList<>();
    private ListenerRegistration l1, l2, l3;
    private final java.util.Set<String> shown = new java.util.HashSet<>();
    private LinearLayout box;
    private String code, codeExpiresText;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Auth.uid() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, getString(R.string.family_title), getString(R.string.signed_in_as, Auth.myName(this))));
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 20));
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        String me = Auth.uid();
        l1 = db.collection("links").whereEqualTo("parentUid", me).addSnapshotListener((snap, e) -> {
            if (snap != null) fill(asParent, snap);
        });
        l2 = db.collection("links").whereEqualTo("childUid", me).addSnapshotListener((snap, e) -> {
            if (snap != null) fill(asChild, snap);
        });
        // 자녀가 보낸 화면 공유 요청이 오면 큰 허용/거부 화면을 띄운다(이 화면이 열려 있을 때).
        l3 = db.collection("sessions").whereEqualTo("parentUid", me).whereEqualTo("status", "requested")
                .addSnapshotListener((snap, e) -> {
                    if (snap == null) return;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        if (!shown.add(d.getId())) continue;
                        startActivity(new Intent(this, ConsentActivity.class)
                                .putExtra(ConsentActivity.EXTRA_SESSION, d.getId())
                                .putExtra(ConsentActivity.EXTRA_NAME, nz(d.getString("childName"))));
                    }
                });
        render();
    }

    private void fill(List<DocumentSnapshot> to, QuerySnapshot snap) {
        to.clear();
        to.addAll(snap.getDocuments());
        render();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (l1 != null) l1.remove();
        if (l2 != null) l2.remove();
        if (l3 != null) l3.remove();
    }

    // ------------------------------------------------------------------ 화면
    private void render() {
        if (box == null) return;
        box.removeAllViews();
        box.addView(Ui.text(this, getString(R.string.family_intro), 14, Ui.GRAY, false));

        // 내가 도움받는 사람: 초대 코드 + 들어온 요청
        section(getString(R.string.parent_section));
        Button mk = Ui.button(this, getString(R.string.btn_make_code), Ui.CORAL, Color.WHITE);
        mk.setOnClickListener(v -> makeCode(0));
        box.addView(mk, lp());
        if (code != null) {
            TextView t = Ui.text(this, getString(R.string.code_valid, code, INVITE_MINUTES), 16, Ui.INK, true);
            t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
            box.addView(t);
            ImageView qr = new ImageView(this);
            qr.setImageBitmap(qr("vzk://join?c=" + code, Ui.dp(this, 190)));
            box.addView(qr, new LinearLayout.LayoutParams(Ui.dp(this, 190), Ui.dp(this, 190)));
        }
        box.addView(reportBox());
        List<DocumentSnapshot> pend = new ArrayList<>();
        for (DocumentSnapshot d : asParent) if ("pending".equals(d.getString("status"))) pend.add(d);
        if (!pend.isEmpty()) {
            section(getString(R.string.pending_title));
            for (final DocumentSnapshot d : pend) {
                final String nm = shownName(d, true);
                box.addView(Ui.text(this, nm, 16, Ui.INK, true));
                LinearLayout row = new LinearLayout(this);
                Button ok = Ui.button(this, getString(R.string.btn_accept), Ui.GREEN, Color.WHITE);
                ok.setOnClickListener(v -> confirm(getString(R.string.confirm_accept, nm), () -> accept(d)));
                Button no = Ui.button(this, getString(R.string.btn_reject), Ui.RED, Color.WHITE);
                no.setOnClickListener(v -> d.getReference().delete());
                row.addView(ok, Ui.weight(this, 3));
                row.addView(no, Ui.weight(this, 3));
                box.addView(row);
            }
        }

        // 내가 도와주는 사람: 코드 입력 / QR
        section(getString(R.string.child_section));
        LinearLayout row = new LinearLayout(this);
        Button en = Ui.button(this, getString(R.string.btn_enter_code), Ui.NAVY, Color.WHITE);
        en.setOnClickListener(v -> askCode());
        Button sc = Ui.button(this, getString(R.string.btn_scan_qr), Ui.NAVY, Color.WHITE);
        sc.setOnClickListener(v -> new IntentIntegrator(this)
                .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE).setBeepEnabled(false).setOrientationLocked(true).initiateScan());
        row.addView(en, Ui.weight(this, 3));
        row.addView(sc, Ui.weight(this, 3));
        box.addView(row);

        // 연결 목록
        section(getString(R.string.members_title));
        int n = 0;
        for (final DocumentSnapshot d : asParent) {
            String st = d.getString("status");
            if ("pending".equals(st)) continue;
            n++;
            memberRow(d, true, "blocked".equals(st));
        }
        for (final DocumentSnapshot d : asChild) {
            if ("blocked".equals(d.getString("status"))) continue;
            n++;
            memberRow(d, false, false);
        }
        if (n == 0) box.addView(Ui.text(this, getString(R.string.members_empty), 14, Ui.GRAY, false));

        if (!asChild.isEmpty()) {
            Button rp = Ui.button(this, Tr.t("📋 부모님 폰 점검 리포트"), Color.WHITE, Ui.INK);
            rp.setOnClickListener(v -> startActivity(new Intent(this, ReportActivity.class)));
            LinearLayout.LayoutParams lr = lp();
            lr.topMargin = Ui.dp(this, 22);
            box.addView(rp, lr);
        }
        Button sub = Ui.button(this, getString(R.string.btn_subscription), Color.WHITE, Ui.INK);
        sub.setOnClickListener(v -> startActivity(new Intent(this, SubscribeActivity.class)));
        LinearLayout.LayoutParams ls = lp();
        ls.topMargin = Ui.dp(this, 22);
        box.addView(sub, ls);
        Button logs = Ui.button(this, getString(R.string.btn_logs), Color.WHITE, Ui.INK);
        logs.setOnClickListener(v -> startActivity(new Intent(this, LogActivity.class)));
        box.addView(logs, lp());
        Button lang = Ui.button(this, getString(R.string.btn_language), Color.WHITE, Ui.INK);
        lang.setOnClickListener(v -> startActivity(new Intent(this, LanguageActivity.class)));
        LinearLayout.LayoutParams l = lp();
        box.addView(lang, l);
        Button out = Ui.button(this, getString(R.string.btn_signout), Color.WHITE, Ui.RED);
        out.setOnClickListener(v -> {
            Auth.signOut(this);
            finish();
        });
        box.addView(out, lp());
        TextView ver = Ui.text(this, getString(R.string.version_line, BuildConfig.VERSION_NAME), 12, Ui.GRAY, false);
        ver.setGravity(android.view.Gravity.CENTER);
        ver.setPadding(0, Ui.dp(this, 12), 0, 0);
        box.addView(ver);
    }

    /** 상대 이름: 내가 붙인 별명이 있으면 그것, 없으면 상대 이름. 같은 이름이 여럿이면 ID 끝 4자리를 붙여 구분한다. */
    private String shownName(DocumentSnapshot d, boolean iAmParent) {
        String uid = nz(iAmParent ? d.getString("childUid") : d.getString("parentUid"));
        String alias = getSharedPreferences("aliases", MODE_PRIVATE).getString(uid, null);
        if (alias != null && !alias.isEmpty()) return alias;
        String name = nz(iAmParent ? d.getString("childName") : d.getString("parentName"));
        int same = 0;
        for (DocumentSnapshot x : asParent) if (name.equals(nz(x.getString("childName")))) same++;
        for (DocumentSnapshot x : asChild) if (name.equals(nz(x.getString("parentName")))) same++;
        if (same > 1 && uid.length() >= 4) return name + " (" + uid.substring(uid.length() - 4) + ")";
        return name;
    }

    private void rename(final DocumentSnapshot d, final boolean iAmParent) {
        final String uid = nz(iAmParent ? d.getString("childUid") : d.getString("parentUid"));
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setSingleLine(true);
        String cur = getSharedPreferences("aliases", MODE_PRIVATE).getString(uid, "");
        et.setText(cur);
        et.setHint(Tr.t("예: 어머니, 큰아버지"));
        new AlertDialog.Builder(this).setTitle(Tr.t("이름 바꾸기"))
                .setMessage(Tr.t("내 폰에서만 보이는 이름입니다. 비우면 원래 이름으로 돌아갑니다."))
                .setView(et)
                .setPositiveButton(getString(R.string.btn_ok), (x, w) -> {
                    getSharedPreferences("aliases", MODE_PRIVATE).edit().putString(uid, et.getText().toString().trim()).apply();
                    render();
                })
                .setNegativeButton(getString(R.string.btn_cancel), null).show();
    }

    private void memberRow(final DocumentSnapshot d, final boolean iAmParent, boolean blocked) {
        final String other = shownName(d, iAmParent);
        String role = iAmParent ? getString(R.string.role_helper) : getString(R.string.role_helped);
        TextView t = Ui.text(this, "👤 " + other + "  ·  " + role + (blocked ? "  ·  " + getString(R.string.status_blocked) : ""), 16, Ui.INK, true);
        t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        box.addView(t);
        String req = d.getString("deleteRequestedBy");
        if (req != null && !req.equals(Auth.uid())) {
            box.addView(Ui.text(this, getString(R.string.disconnect_incoming, other), 13, Ui.AMBER, true));
        } else if (req != null) {
            box.addView(Ui.text(this, getString(R.string.disconnect_requested), 13, Ui.AMBER, false));
        }
        LinearLayout row = new LinearLayout(this);
        Button rn = Ui.button(this, Tr.t("이름 바꾸기"), Color.WHITE, Ui.INK);
        rn.setTextSize(13);
        rn.setOnClickListener(v -> rename(d, iAmParent));
        row.addView(rn, Ui.weight(this, 3));
        if (iAmParent) {
            Button rm = Ui.button(this, getString(R.string.btn_remove), Color.WHITE, Ui.RED);
            rm.setTextSize(13);
            rm.setOnClickListener(v -> confirm(getString(R.string.confirm_remove, other), () -> d.getReference().delete()));
            row.addView(rm, Ui.weight(this, 3));
            if (!blocked) {
                Button bl = Ui.button(this, getString(R.string.btn_block), Color.WHITE, Ui.INK);
                bl.setTextSize(13);
                bl.setOnClickListener(v -> confirm(getString(R.string.confirm_block, other),
                        () -> d.getReference().update("status", "blocked")));
                row.addView(bl, Ui.weight(this, 3));
            }
        } else if (req == null) {
            Button ask = Ui.button(this, getString(R.string.btn_ask_help), Ui.CORAL, Color.WHITE);
            ask.setTextSize(13);
            ask.setOnClickListener(v -> Access.check((ok, via) -> runOnUiThread(() -> {
                if (!ok) {
                    startActivity(new Intent(this, SubscribeActivity.class));
                    return;
                }
                startActivity(new Intent(this, ViewerActivity.class)
                        .putExtra(ViewerActivity.EXTRA_PARENT_UID, d.getString("parentUid"))
                        .putExtra(ViewerActivity.EXTRA_PARENT_NAME, other));
            })));
            if ("active".equals(d.getString("status"))) row.addView(ask, Ui.weight(this, 3));
            Button rq = Ui.button(this, getString(R.string.btn_request_disconnect), Color.WHITE, Ui.RED);
            rq.setTextSize(13);
            rq.setOnClickListener(v -> d.getReference().update("deleteRequestedBy", Auth.uid()));
            row.addView(rq, Ui.weight(this, 3));
        }
        box.addView(row);
    }

    // ------------------------------------------------------------------ 동작
    private void makeCode(final int attempt) {
        SecureRandom r = new SecureRandom();
        final String c = String.format("%08d", r.nextInt(100000000));
        long now = System.currentTimeMillis();
        Map<String, Object> m = new HashMap<>();
        m.put("parentUid", Auth.uid());
        m.put("parentName", Auth.myName(this));
        m.put("createdAt", new Timestamp(new Date(now)));
        m.put("expiresAt", new Timestamp(new Date(now + INVITE_MINUTES * 60_000L)));
        m.put("used", false);
        db.collection("invites").document(c).set(m)
                .addOnSuccessListener(x -> {
                    code = c;
                    render();
                })
                .addOnFailureListener(e -> {
                    if (attempt < 3) makeCode(attempt + 1);
                    else Ui.toast(this, getString(R.string.err_generic, Auth.msg(e)));
                });
    }

    private void askCode() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint(getString(R.string.code_hint));
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.btn_enter_code))
                .setView(et)
                .setPositiveButton(getString(R.string.btn_ok), (d, w) -> join(et.getText().toString().trim()))
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    /** 자녀: 코드로 연결 요청을 만든다(수락 전에는 아무 권한이 없다). */
    private void join(final String c) {
        if (c.length() != 8) {
            Ui.toast(this, getString(R.string.err_code_invalid));
            return;
        }
        db.collection("invites").document(c).get().addOnSuccessListener(inv -> {
            Timestamp exp = inv.getTimestamp("expiresAt");
            String parentUid = inv.getString("parentUid");
            if (!inv.exists() || parentUid == null || exp == null || exp.toDate().before(new Date())
                    || Boolean.TRUE.equals(inv.getBoolean("used")) || parentUid.equals(Auth.uid())) {
                Ui.toast(this, getString(R.string.err_code_invalid));
                return;
            }
            final String pName = nz(inv.getString("parentName"));
            Map<String, Object> m = new HashMap<>();
            m.put("parentUid", parentUid);
            m.put("parentName", pName);
            m.put("childUid", Auth.uid());
            m.put("childName", Auth.myName(this));
            m.put("status", "pending");
            m.put("inviteCode", c);
            m.put("createdAt", FieldValue.serverTimestamp());
            db.collection("links").document(parentUid + "_" + Auth.uid()).set(m)
                    .addOnSuccessListener(x -> Ui.toast(this, getString(R.string.request_sent, pName)))
                    .addOnFailureListener(e -> Ui.toast(this, getString(R.string.err_code_invalid)));
        }).addOnFailureListener(e -> Ui.toast(this, getString(R.string.err_generic, Auth.msg(e))));
    }

    private void accept(DocumentSnapshot d) {
        String c = d.getString("inviteCode");
        com.google.firebase.firestore.WriteBatch wb = db.batch();
        wb.update(d.getReference(), "status", "active");
        if (c != null) wb.update(db.collection("invites").document(c), "used", true);
        wb.commit().addOnFailureListener(e -> Ui.toast(this, getString(R.string.err_generic, Auth.msg(e))));
        code = null;
    }

    /** 부모: 위험 앱 설치 알림을 자녀에게 보낼지 동의하는 곳. 동의해야 켜진다. */
    private View reportBox() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        final boolean on = Report.on(this);
        Button t = Ui.button(this, Tr.t(on ? "자녀에게 위험 앱 알림: 켜짐 (끄기)" : "자녀에게 위험 앱 알림 켜기"), on ? Color.WHITE : Ui.GREEN, on ? Ui.INK : Color.WHITE);
        t.setTextSize(14);
        t.setOnClickListener(v -> {
            if (on) {
                Report.setOn(this, false);
                render();
            } else {
                confirm(Tr.t("위험이 의심되는 앱이 이 폰에 설치되면, 연결된 자녀(구독자)에게 앱 이름과 위험 이유만 알립니다.\n사진·메시지·통화 내용 등 개인 정보는 보내지 않습니다. 언제든 끌 수 있습니다.\n\n켤까요?"), () -> {
                    Report.setOn(this, true);
                    render();
                });
            }
        });
        b.addView(t, lp());
        b.addView(Ui.text(this, Tr.t("부모님 동의로만 켜집니다. 알림은 구독 중인 자녀에게만 갑니다."), 13, Ui.GRAY, false));
        return b;
    }

    private void confirm(String msg, final Runnable yes) {
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setPositiveButton(getString(R.string.btn_ok), (DialogInterface d, int w) -> yes.run())
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        IntentResult r = IntentIntegrator.parseActivityResult(req, res, data);
        if (r != null && r.getContents() != null) {
            String s = r.getContents();
            int i = s.indexOf("c=");
            join(i >= 0 ? s.substring(i + 2).trim() : s.trim());
        }
    }

    // ------------------------------------------------------------------ 도우미
    private Bitmap qr(String text, int px) {
        try {
            BitMatrix bm = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px);
            Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.RGB_565);
            for (int x = 0; x < px; x++) for (int y = 0; y < px; y++) bmp.setPixel(x, y, bm.get(x, y) ? Color.BLACK : Color.WHITE);
            return bmp;
        } catch (Exception e) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
        }
    }

    private void section(String s) {
        TextView t = Ui.text(this, s, 17, Ui.NAVY, true);
        t.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 6));
        box.addView(t);
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.setMargins(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        return l;
    }

    private static String nz(String s) {
        return s == null || s.isEmpty() ? "?" : s;
    }
}
