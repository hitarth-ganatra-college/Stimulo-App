package com.stimulo.app.esp;

import android.util.Log;

/**
 * Stub implementation of Esp32Communicator.
 * Replace or extend this with actual BLE/Wi-Fi transport when ESP32 hardware is ready.
 *
 * TODO: Replace stub with BleEsp32Communicator or WifiEsp32Communicator
 */
public class StubEsp32Communicator implements Esp32Communicator {
    private static final String TAG = "StubEsp32Comm";

    @Override
    public void sendBuzzCommand(long scheduleId, String command, AckCallback callback) {
        // TODO: Establish BLE/Wi-Fi connection to ESP32 and send `command`
        // TODO: Parse response and call callback.onAck() or callback.onFailure()
        Log.i(TAG, "[STUB] sendBuzzCommand scheduleId=" + scheduleId + " cmd=" + command);
        // Simulate success for now
        if (callback != null) {
            callback.onAck(scheduleId, "ACK:" + scheduleId);
        }
    }

    @Override
    public void ping(AckCallback callback) {
        // TODO: Send PING to ESP32 and await ACK:PING within timeout
        Log.i(TAG, "[STUB] ping");
        if (callback != null) {
            callback.onAck(-1, "ACK:PING");
        }
    }
}
