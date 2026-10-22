package kr.todanjeon.phoneclean;

import java.util.ArrayList;
import java.util.List;

/** 점검 결과 한 건(앱 하나). */
final class AppEntry {
    String pkg;
    String label;
    long installTime;
    String installerText;
    int score;
    int hard; // 겹친 '강한 근거'의 수
    boolean trusted;
    boolean accEnabled;
    boolean adminActive;
    boolean preloaded;
    boolean isHome;
    boolean safe; // 사용자가 직접 안전 표시
    boolean autoSafe; // 최근에 직접 열어 본 앱 → 안전 후보
    boolean overlay;
    boolean installAllowed;
    boolean tiny;
    boolean mismatch;
    boolean bootCombo;
    boolean keyStrong;
    boolean clustered;
    boolean neverOpened;
    boolean longUnused;
    boolean usedRecently;
    boolean usageKnown;
    long lastUsed;
    int adCount;
    String signer;
    final List<String> reasons = new ArrayList<>();
    final List<String> tags = new ArrayList<>();

    static final int LEVEL_HIGH = 2;
    static final int LEVEL_WARN = 1;
    static final int LEVEL_LOW = 0;

    int level() {
        if (safe || autoSafe) return LEVEL_LOW;
        if (score >= 6 && hard >= 2) return LEVEL_HIGH;
        if (score >= 3) return LEVEL_WARN;
        return LEVEL_LOW;
    }

    int sortScore() {
        return (safe || autoSafe) ? -1 : score;
    }

    String levelText() {
        if (safe) return "안전 표시";
        if (autoSafe) return "안전 후보";
        switch (level()) {
            case LEVEL_HIGH:
                return "위험 의심";
            case LEVEL_WARN:
                return "주의";
            default:
                return "낮음";
        }
    }

    String tagText() {
        StringBuilder sb = new StringBuilder();
        for (String t : tags) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(t);
        }
        return sb.toString();
    }
}
