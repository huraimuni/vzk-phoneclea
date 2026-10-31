package kr.todanjeon.phoneclean;

import com.google.firebase.firestore.FirebaseFirestore;

/** 관리자 여부. config/admin 문서를 읽을 수 있으면 관리자(규칙이 uids 목록으로 판단). 앱 화면 표시용일 뿐, 실제 권한은 서버 규칙이 판단한다. */
final class Admin {
    static volatile boolean is;

    private Admin() {
    }

    static void refresh() {
        final String me = Auth.uid();
        if (me == null) {
            is = false;
            return;
        }
        FirebaseFirestore.getInstance().collection("config").document("admin").get()
                .addOnCompleteListener(t -> is = t.isSuccessful() && t.getResult() != null && t.getResult().exists());
    }
}
