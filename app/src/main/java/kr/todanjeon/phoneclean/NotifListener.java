package kr.todanjeon.phoneclean;

import android.service.notification.NotificationListenerService;

/**
 * 알림 비우기 전용. 알림 내용은 읽지 않고(앱 이름과 개수만 셈), 저장하거나 밖으로 보내지 않습니다.
 * 사용자가 [알림 접근]을 직접 켰을 때만 동작합니다.
 */
public class NotifListener extends NotificationListenerService {
    static volatile NotifListener instance;

    @Override
    public void onListenerConnected() {
        instance = this;
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
    }
}
