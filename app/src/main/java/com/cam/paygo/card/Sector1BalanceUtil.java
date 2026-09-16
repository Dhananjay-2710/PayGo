package com.cam.paygo.card;

// imports you need

import android.util.Log;

import com.pax.dal.IPicc;
import com.pax.dal.entity.EM1KeyType;
import com.pax.dal.exceptions.PiccDevException; // use your SDK exception type if different

import java.util.Arrays;

public class Sector1BalanceUtil {
    private static final String TAG = "Sector1BalanceUtil";
    private final IPicc piccReader;
    private static final byte[] DEFAULT_KEY_A = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

    public Sector1BalanceUtil(IPicc piccReader) {
        this.piccReader = piccReader;
    }

    public boolean authenticateSector(byte[] uid) {
        try {
//            byte[] defaultKeyA = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
//                    (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
            piccReader.m1Auth(EM1KeyType.TYPE_A, (byte) 4, DEFAULT_KEY_A, uid);
            Log.i(TAG, "Auth OK for Sector 1 with Key A");
            return true;
        } catch (PiccDevException e) {
            Log.e(TAG, "Auth failed for Sector 1", e);
            return false;
        }
    }

    public byte[] m1Read(byte blkNo) {
        try {
            byte[] result = piccReader.m1Read(blkNo);
            Log.d(TAG, "M1 Read");
            Log.d(TAG, "result" + Arrays.toString(result));
            return result;
        } catch (PiccDevException e) {
            e.printStackTrace();
            Log.d(TAG, "M1 Read error : " + e.getMessage());
            return null;
        }
    }

    public void m1Write(byte blkNo, int i) {
        try {
            byte[] BlkValue = new byte[16];
            piccReader.m1Write(blkNo, BlkValue);
            Log.d(TAG, "M1 Write");
        } catch (PiccDevException e) {
            e.printStackTrace();
            Log.d(TAG, "M1 Read error : " + e.getMessage());
        }
    }
}