package com.company;

import java.nio.ByteBuffer;

public class ByteUtils {

    public static long bytesToLong(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getLong();
    }

    public static int bytesToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    public static byte[] intToBytes(int val) {
        ByteBuffer iBuffer = ByteBuffer.allocate(Integer.BYTES);
        iBuffer.putInt(val);
        return iBuffer.array();
    }
}
