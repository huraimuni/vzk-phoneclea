package kr.todanjeon.phoneclean;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 폰을 다시 켠 뒤에도 감시가 켜져 있도록 서비스를 되살립니다. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            boolean on = context.getSharedPreferences(NewAppJob.PREFS, Context.MODE_PRIVATE).getBoolean("watch_on", false);
            if (on) {
                NewAppJob.schedule(context);
            }
        } catch (Exception ignored) {
        }
    }
}
