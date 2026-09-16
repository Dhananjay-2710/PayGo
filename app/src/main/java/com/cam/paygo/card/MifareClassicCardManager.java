package com.cam.paygo.card;

import android.util.Log;

import com.pax.dal.IPicc;
import com.pax.dal.entity.EM1KeyType;
import com.pax.dal.exceptions.PiccDevException;

import java.util.Arrays;

public class MifareClassicCardManager {
    private static final String TAG = "MifareClassicCardManager";
    private final IPicc piccReader;
    private static final byte[] DEFAULT_KEY_A = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

    public MifareClassicCardManager(IPicc piccReader) {
        this.piccReader = piccReader;
    }

    public boolean authenticateSector(byte[] uid, byte bloNum) {
        try {
            piccReader.m1Auth(EM1KeyType.TYPE_A, bloNum, DEFAULT_KEY_A, uid);
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
            Log.d(TAG, "M1 Read Result :" + Arrays.toString(result));
            return result;
        } catch (PiccDevException e) {
            e.printStackTrace();
            Log.d(TAG, "M1 Read error : " + e.getMessage());
            return null;
        }
    }

    // The corrected m1Write method
    public void m1Write(byte blkNo, byte[] dataToWrite) {
        try {
            // Add a check to ensure the data is exactly 16 bytes long
            if (dataToWrite == null || dataToWrite.length != 16) {
                Log.e(TAG, "M1 Write error: Data must be 16 bytes long.");
                return;
            }

            // Write the ACTUAL data that was passed into the method
            piccReader.m1Write(blkNo, dataToWrite);
            Log.d(TAG, "M1 Write successful with data: " + Arrays.toString(dataToWrite));
        } catch (PiccDevException e) {
            e.printStackTrace();
            Log.d(TAG, "M1 Write error : " + e.getMessage());
        }
    }
}