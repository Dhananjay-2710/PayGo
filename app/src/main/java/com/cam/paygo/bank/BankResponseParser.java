package com.cam.paygo.bank;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.cam.paygo.constants.BankConstants;
import com.cam.paygo.constants.JsonKeys;
import com.cam.paygo.utils.AppLogger;

import org.json.JSONException;
import org.json.JSONObject;

public class BankResponseParser {

    private static final String TAG = "BankResponseParser";
    private final Context context;

    public BankResponseParser(Context context) {
        this.context = context;
    }

    public BankResponse parse(Intent data) {

        if (data == null || data.getExtras() == null) {
            throw new IllegalArgumentException("Intent or extras is null");
        }

        String resultFromABP = data.getExtras().getString(JsonKeys.RESULT);
        Log.d(TAG, "Result : " + resultFromABP);
        if (resultFromABP == null || resultFromABP.isEmpty()) {
            throw new IllegalArgumentException("Bank RESULT extra is missing or empty");
        }

        String cleanResult = resultFromABP
                .replace(BankConstants.STX, "")
                .replace(BankConstants.ETX, "")
                .trim();

        JSONObject root;
        try {
            root = new JSONObject(cleanResult);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid bank RESULT JSON", e);
        }

        String responseType = root.optString(JsonKeys.RESPONSE_TYPE, "");
        String statusCode = root.optString(JsonKeys.STATUS_CODE, "");
        String statusMsg = root.optString(JsonKeys.STATUS_MSG, "");
        String erpTranId = root.optString(JsonKeys.ERP_TRAN_ID, "");

        JSONObject receipt = root.optJSONObject(JsonKeys.RECEIPT_DATA);

        Log.d(TAG, "Response Type :" + responseType);
        Log.d(TAG, "Status Code :" + statusCode);
        Log.d(TAG, "Status Msg : " + statusMsg);
        Log.d(TAG, "ERPTranId : " + erpTranId);

        AppLogger.trxn_log(context, BankConstants.BANK_INBOUND, cleanResult);

        return new BankResponse(
                responseType,
                statusCode,
                statusMsg,
                erpTranId,
                new ReceiptData(receipt),
                cleanResult
        );
    }
}