package com.cam.paygo.card;

import static com.cam.paygo.MainActivity.getDeviceSerialNumber;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.cam.paygo.bank.ABPBank;
import com.pax.dal.IDAL;
import com.pax.dal.IPicc;
import com.pax.dal.entity.EDetectMode;
import com.pax.dal.entity.EPiccType;
import com.pax.dal.entity.PiccCardInfo;
import com.pax.dal.exceptions.PiccDevException;
import com.pax.neptunelite.api.NeptuneLiteUser;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CardDetector {

    private static final String TAG = "CardDetector";
    private final Context context;
    private Callback callback;
    private IPicc piccReader = null;
    private long topupAmount = 0;
    private String tranType, SOURCE_TXN_ID, OPERATOR_ORDER_ID, INVOICE_NO, RRN, IS_OFFLINE, SHIFT_NO, STATION_NAME, STATION_ID, GATENO, PRINT_FLAG, deviceSerialNumber, UDF1, UDF2, UDF3, UDF4, UDF5 = "";
    private boolean isPolling = false;
    private String pollingData;
    private String dbTxnId;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object piccLock = new Object();
    private ABPBank abpBank;

    public interface Callback {
        void onCardDetected(
                String cardType,
                String uid,
                String balance,
                Intent intentToLaunch,
                String tranType,
                String topupAmount,
                String sourceTxnId,
                String dbTxnId);

        void onError(String msg);
    }

    public CardDetector(Context ctx, IDAL iDal) throws Exception {
        this.context = ctx;
        iDal = NeptuneLiteUser.getInstance().getDal(ctx);
        piccReader = iDal.getPicc(EPiccType.INTERNAL);
        abpBank = new ABPBank(ctx);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    // ------------------------------------------------------------------------
    // START POLLING
    // ------------------------------------------------------------------------
    public void startPolling(String incomingRequestJson, String dbTxnId) {
        this.pollingData = incomingRequestJson;
        this.dbTxnId = dbTxnId;
        isPolling = true;
        executor.submit(pollingTask);
    }

    // ------------------------------------------------------------------------
    // STOP POLLING + RELEASE PICC
    // ------------------------------------------------------------------------
    public void stopPolling() {
        isPolling = false;

        synchronized (piccLock) {
            try {
                if (piccReader != null) {
                    piccReader.close();
                }
//                piccReader.piccDeactivate();
//                piccReader.piccPowerOff();
            } catch (Exception ex) {
                Log.w(TAG, "stopPolling: picc close failed", ex);
            }
        }
    }

    // ------------------------------------------------------------------------
    // SIMPLE CARD IDENTIFICATION (NO APDU)
    // ------------------------------------------------------------------------
    private String identifyCardSimple(PiccCardInfo info) {
        int type = info.getCardType();

        // NCMC / EMV CPU cards (ISO14443-A/B)
        if (type == 1 || type == 65 || type == 66) {
            return "NCMC";
        }

        if (type == 77) {
            return "MIFARE";
        }

        return "UNSUPPORTED";
    }

    // ------------------------------------------------------------------------
    // MAIN POLLING TASK
    // ------------------------------------------------------------------------
    private final Runnable pollingTask = () -> {
        try {
            JSONObject root = new JSONObject(pollingData);
            JSONObject dataObj = root.getJSONObject("DATA");

            tranType = dataObj.optString("TRAN_TYPE", "");
            topupAmount = dataObj.optLong("AMOUNT", 0);
            SOURCE_TXN_ID = dataObj.optString("SOURCE_TXN_ID", "NA");
            OPERATOR_ORDER_ID = dataObj.optString("OPERATOR_ORDER_ID", "NA");
            INVOICE_NO = dataObj.optString("INVOICE_NO", "NA");
            RRN = dataObj.optString("RRN", "NA");
            IS_OFFLINE = dataObj.optString("IS_OFFLINE", "0");
            PRINT_FLAG = dataObj.optString("PRINT_FLAG", "1");
            SHIFT_NO = dataObj.optString("SHIFT_NO", "NA");
            STATION_NAME = dataObj.optString("STATION_NAME", "NA");
            STATION_ID = dataObj.optString("STATION_ID", "NA");
            GATENO = dataObj.optString("GATENO", "NA");
            deviceSerialNumber = getDeviceSerialNumber();
            UDF1 = dataObj.optString("UDF1", "");
            UDF2 = dataObj.optString("UDF2", "");
            UDF3 = dataObj.optString("UDF3", deviceSerialNumber);
            UDF4 = dataObj.optString("UDF4", "");
            UDF5 = dataObj.optString("UDF5", "");
            Log.d(TAG, "OPERATOR_ORDER_ID : " + OPERATOR_ORDER_ID);
            Log.d(TAG, "INVOICE_NO: " + INVOICE_NO);
            Log.d(TAG, "RRN : " + RRN);

        } catch (Exception e) {
            Log.e(TAG, "JSON error: " + e.getMessage());
            stopPolling();
            if (callback != null) callback.onError("Request parsing failed");
            return;
        }

        long startTime = System.currentTimeMillis();
        long timeout = 40_000;

        try {
            synchronized (piccLock) {
                piccReader.open();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open PICC: " + e.getMessage());
            stopPolling();
            return;
        }

        while (isPolling) {
            try {
                PiccCardInfo info;

                synchronized (piccLock) {
                    info = piccReader.detect(EDetectMode.ISO14443_AB);
                }

                if (info != null) {
                    String uid = bytesToHex(info.getSerialInfo());
                    String type = identifyCardSimple(info);

                    // Stop polling *before launching bank app*
                    stopPolling();

                    // Mandatory delay for IM30/A30 hardware RF reset
                    Thread.sleep(10);
                    Intent bankIntent = null;
                    String balance = "";
                    switch (type) {
                        case "NCMC":
                            Log.d(TAG, "👉 NCMC card detected, launching Bank App");
                            bankIntent =
                                    abpBank.createBankAppIntent(tranType,
                                            String.valueOf(topupAmount),
                                            SOURCE_TXN_ID, dbTxnId, OPERATOR_ORDER_ID, INVOICE_NO, RRN, IS_OFFLINE, PRINT_FLAG, SHIFT_NO, STATION_NAME, STATION_ID, GATENO, UDF1, UDF2, UDF3, UDF4, UDF5);
                            break;

                        case "DESFire":
                            // TODO: implement DESFire logic
                            break;

                        case "Unknown Card Type":
                            balance = MIFAREClassic(info, tranType);
                            break;

                        default:
                            Log.w(TAG, "⚠️ Unsupported card type: " + type);
                            break;
                    }

                    if (callback != null) {
                        switch (type) {
                            case "NCMC":
                                Intent finalBankIntent = bankIntent;
                                new Handler(Looper.getMainLooper()).post(() ->
                                        callback.onCardDetected(
                                                type,
                                                uid,
                                                "",
                                                finalBankIntent,
                                                tranType,
                                                String.valueOf(topupAmount),
                                                SOURCE_TXN_ID,
                                                dbTxnId
                                        )
                                );
                                break;

                            case "DESFire":
                                // TODO: implement DESFire logic
                                break;

                            case "Unknown Card Type":
                                String finalBalance = balance;
                                new Handler(Looper.getMainLooper()).post(
                                        () -> callback.onCardDetected(type, uid, finalBalance, null, tranType, String.valueOf(topupAmount), SOURCE_TXN_ID, dbTxnId)
                                );
                                break;

                            default:
                                Log.w(TAG, "⚠️ Unsupported card type: " + type);
                                break;
                        }
                    }
                    return;
                }

                if (System.currentTimeMillis() - startTime > timeout) {
                    stopPolling();
                    if (callback != null)
                        callback.onError("Timeout waiting for card");
                    return;
                }

                Thread.sleep(200);

            } catch (PiccDevException e) {
                Log.e(TAG, "PICC error: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error: " + e.getMessage(), e);
            }
        }
    };

    // ------------------------------------------------------------------------
    // UTILS
    // ------------------------------------------------------------------------
    private static String bytesToHex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte value : b) sb.append(String.format("%02X", value));
        return sb.toString();
    }

    public String MIFAREClassic(PiccCardInfo cardInfo, String tranType) {

        byte blockNum = (byte) 4;
        Log.d(TAG, "Inside Switch case for MIFARE Classic");
        // Init
        MifareClassicCardManager piccReaderNew = new MifareClassicCardManager(piccReader);

        if (Objects.equals(tranType, "BALANCEENQUIRY")) {
            try {
                // Auth
                boolean authOk = piccReaderNew.authenticateSector(cardInfo.getSerialInfo(), blockNum);
                Log.d(TAG, "AUTH : " + authOk);
                if (!authOk) {
                    Log.e(TAG, "Authentication failed. Cannot continue.");

                }

                // Write
//                long newBalance = 0;
//                byte[] balanceBytes = String.valueOf(newBalance).getBytes(StandardCharsets.UTF_8);
//                byte[] dataToWrite = new byte[16];
//                System.arraycopy(balanceBytes, 0, dataToWrite, 0, Math.min(balanceBytes.length, 16));
//
//                piccReaderNew.m1Write(blockNum, dataToWrite);
//                Log.d(TAG, "Successfully wrote: " + Arrays.toString(dataToWrite));

                // Read
                byte[] readBalanceBytes = piccReaderNew.m1Read(blockNum);
                String balanceString = new String(readBalanceBytes, StandardCharsets.UTF_8);
                String cleanBalanceString = balanceString.trim();
//                String cleanBalanceString = String.valueOf(newBalance);
                Log.d(TAG, "Data read from card: " + cleanBalanceString);
                return cleanBalanceString;
            } catch (Exception e) {
                Log.e(TAG, "Classic card exception: " + e.getMessage(), e);
            }
        } else if (Objects.equals(tranType, "BALANCEUPDATE")) {
            Log.d(TAG, "Inside TOP Amount");
            try {
                // Auth
                boolean authOk = piccReaderNew.authenticateSector(cardInfo.getSerialInfo(), blockNum);
                Log.d(TAG, "AUTH : " + authOk);
                if (!authOk) {
                    Log.e(TAG, "Authentication failed. Cannot continue.");

                } else {
                    Log.d(TAG, "Auth Okay Continue");
                }

                // Step 1: Read previous balance
                byte[] readPreviousBalanceBytes = piccReaderNew.m1Read(blockNum);
                String previousBalanceString = new String(readPreviousBalanceBytes, StandardCharsets.UTF_8).trim();
                long previousBalance = 0;
                try {
                    previousBalance = Long.parseLong(previousBalanceString.replaceAll("[^0-9]", ""));
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Previous balance parse failed, defaulting to 0");
                }

                // Step 2: Add top-up amount
                long newBalance = previousBalance + topupAmount;
                Log.d(TAG, "Previous Balance: " + previousBalance + " | Top-up Amount: " + topupAmount + " | New Balance: " + newBalance);

                if (topupAmount == 0) {
                    long newBalanceZero = 0;
                    byte[] balanceBytes = String.valueOf(newBalanceZero).getBytes(StandardCharsets.UTF_8);
                    byte[] dataToWrite = new byte[16];
                    System.arraycopy(balanceBytes, 0, dataToWrite, 0, Math.min(balanceBytes.length, 16));

                    piccReaderNew.m1Write(blockNum, dataToWrite);
                    Log.d("MifareWrite", "Successfully wrote: " + Arrays.toString(dataToWrite));
                } else {
                    // Step 4: Write updated balance
                    byte[] balanceBytes = String.valueOf(newBalance).getBytes(StandardCharsets.UTF_8);
                    byte[] dataToWrite = new byte[16];
                    System.arraycopy(balanceBytes, 0, dataToWrite, 0, Math.min(balanceBytes.length, 16));

                    piccReaderNew.m1Write(blockNum, dataToWrite);
                    Log.d("MifareWrite", "Successfully wrote: " + Arrays.toString(dataToWrite));
                }
                // Step 5: Re-read for verification
                byte[] readBalanceBytes = piccReaderNew.m1Read(blockNum);
                String updatedBalanceString = new String(readBalanceBytes, StandardCharsets.UTF_8).trim();
                Log.d(TAG, "Updated Balance (from card): " + updatedBalanceString);
//                String updatedBalanceString = String.valueOf(newBalance);
                return updatedBalanceString;

            } catch (Exception e) {
                Log.e(TAG, "Classic card exception: " + e.getMessage(), e);
            }
        }
        return null;
    }
}
