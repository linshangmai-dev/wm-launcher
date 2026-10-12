package com.wmhome.fixture;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

/** Same explicit call as the original WM launcher, from an ordinary separate app UID. */
public class FactoryEntryActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent().setComponent(new ComponentName("com.wm.xxqg",
                "com.wm.xxqg.base.activity.LauncherAcitity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
}
