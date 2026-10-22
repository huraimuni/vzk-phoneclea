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
    boolean trusted;
    boolean accEnabled;
    boolean adminActive;
    boolean preloaded;
    boolean isHome;
    boolean safe;
    final List<String> reasons = new ArrayList<>();

    static final int LEVEL_HIGH = 2;
    static final int LEVEL_WARN = 1;
    static final int LEVEL_LOW = 0;

    int level() {
        if (safe) return LEVEL_LOW;
        if (score >= 6) return LEVEL_HIGH;
        if (score >= 3) return LEVEL_WARN;
        return LEVEL_LOW;
    }

    String levelText() {
        if (safe) return "안전 표시";
        switch (level()) {
            case LEVEL_HIGH:
                return "위험 의심";
            case LEVEL_WARN:
                return "주의";
            default:
                return "낮음";
        }
    }
}
