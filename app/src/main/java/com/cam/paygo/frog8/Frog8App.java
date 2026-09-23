package com.cam.paygo.frog8;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import com.cam.paygo.constants.Frog8Constants;
import com.cam.paygo.utils.AppLogger;

/**
 * Builds Frog8 Bundle APP_TO_APP intents (mirror of {@link com.cam.paygo.bank.ABPBank}).
 * Used when acquirer=FROG8 and MQTT txn type is card (Sale).
 */
public final class Frog8App {

    private static final String TAG = "Frog8App";

    private final Context context;

    public Frog8App(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * @param amountRaw MQTT/bank-style amount (e.g. "10.00" or "1000" paise/cents)
     * @param outOrderNo external order / MQTT request_id
     * @param remark optional note (phone/terminal can be embedded)
     * @return Intent or null if Frog8 package is not installed / not resolvable
     */
    public Intent createSaleIntent(String amountRaw, String outOrderNo, String remark) {
        long amountCents = toCents(amountRaw);
        if (amountCents <= 0) {
            Log.e(TAG, "Invalid amount for Frog8 Sale: " + amountRaw);
            return null;
        }
        if (outOrderNo == null || outOrderNo.trim().isEmpty()) {
            Log.e(TAG, "outOrderNo required for Frog8 Sale");
            return null;
        }
        if (!isFrog8Installed()) {
            Log.e(TAG, "Frog8 package not installed: " + Frog8Constants.PACKAGE_NAME);
            return null;
        }

        Intent intent = new Intent();
        intent.setAction(Frog8Constants.ACTION_PAYMENT);
        intent.setPackage(Frog8Constants.PACKAGE_NAME);
        intent.putExtra(Frog8Constants.EXTRA_TRANS_TYPE, Frog8Constants.TRANS_TYPE_SALE);
        intent.putExtra(Frog8Constants.EXTRA_AMOUNT, amountCents);
        intent.putExtra(Frog8Constants.EXTRA_TIP, 0L);
        intent.putExtra(Frog8Constants.EXTRA_OUT_ORDER_NO, outOrderNo.trim());
        if (remark != null && !remark.trim().isEmpty()) {
            intent.putExtra(Frog8Constants.EXTRA_REMARK, remark.trim());
        }

        String logLine = "Frog8 Sale amountCents=" + amountCents
                + " outOrderNo=" + outOrderNo
                + " package=" + Frog8Constants.PACKAGE_NAME;
        Log.i(TAG, logLine);
        AppLogger.trxn_log(context, Frog8Constants.LOG_OUTBOUND, logLine);
        return intent;
    }

    public boolean isFrog8Installed() {
        try {
            context.getPackageManager().getPackageInfo(Frog8Constants.PACKAGE_NAME, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Convert PayGo amount string to cents/paise (Long) per Frog8 guide.
     * "10.00" / "10" → 1000; "1000" with no decimal is treated as already minor units if length suggests paise,
     * otherwise rupees: prefer decimal or whole rupees × 100 when value looks like major units (&lt; 100000 without decimal).
     */
    static long toCents(String raw) {
        if (raw == null) {
            return 0L;
        }
        String v = raw.trim().replace(",", "");
        if (v.isEmpty()) {
            return 0L;
        }
        try {
            if (v.contains(".")) {
                double major = Double.parseDouble(v);
                return Math.round(major * 100.0d);
            }
            long whole = Long.parseLong(v);
            // MQTT bank amounts are typically major units without decimals for small sales;
            // values already in paise are usually large. Treat plain integers as major (rupees) × 100.
            return whole * 100L;
        } catch (NumberFormatException e) {
            Log.e(TAG, "toCents parse failed: " + raw, e);
            return 0L;
        }
    }
}
