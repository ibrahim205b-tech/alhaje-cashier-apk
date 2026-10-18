package iq.haji.cashier;

import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.net.Uri;
import android.content.SharedPreferences;
import android.os.Build;

public class CallerScreeningService extends CallScreeningService {

    @Override
    public void onScreenCall(Call.Details details) {
        try {
            if (Build.VERSION.SDK_INT >= 29
                    && details.getCallDirection()
                    == Call.Details.DIRECTION_INCOMING) {

                Uri handle = details.getHandle();

                if (handle != null
                        && "tel".equals(handle.getScheme())) {

                    String number = handle.getSchemeSpecificPart();

                    if (number != null && !number.trim().isEmpty()) {
                        SharedPreferences prefs =
                                getSharedPreferences(
                                        "haji_cashier",
                                        MODE_PRIVATE
                                );

                        prefs.edit()
                                .putString("latest_caller", number)
                                .putLong(
                                        "caller_time",
                                        System.currentTimeMillis()
                                )
                                .apply();
                    }
                }
            }
        } finally {
            CallResponse response =
                    new CallResponse.Builder()
                            .setDisallowCall(false)
                            .setRejectCall(false)
                            .build();

            respondToCall(details, response);
        }
    }
}
