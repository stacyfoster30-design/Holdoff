package app.holdoff.main;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MmsDeliverReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Registered so HoldOff qualifies for the SMS role. MMS parsing can be added in the next inbox pass.
    }
}
