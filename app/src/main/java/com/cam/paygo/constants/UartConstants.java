package com.cam.paygo.constants;

public final class UartConstants {
    private UartConstants() {
    }

    public static final String UART_ATTR = "115200,8,n,1";
    public static final int USB_RECONNECT_DELAY_MS = 1500;
    public static final int RECV_TIMEOUT_MS = 500;
    public static final int RECV_BUFFER_BYTES = 512;
    public static final int LISTENER_SLEEP_MS = 5;
    /** Wait after a real port failure before calling recv() again. */
    public static final int RECOVERY_DELAY_MS = 1000;
    /** Hardware settle time while replacing uartComm. */
    public static final int REINIT_SETTLE_MS = 300;
}
