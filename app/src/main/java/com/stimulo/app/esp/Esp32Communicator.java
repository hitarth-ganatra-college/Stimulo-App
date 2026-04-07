package com.stimulo.app.esp;

/**
 * Abstraction layer for ESP32 communication.
 * Implement this interface with Bluetooth/Wi-Fi transport when ESP32 integration is ready.
 *
 * Protocol (suggested):
 *   PING                          → ACK:PING
 *   BUZZ:<duration_ms>:<pattern>  → ACK:<event_id> | ERR:<code>
 *   SCHEDULE_TEST:<event_id>      → ACK:<event_id>
 */
public interface Esp32Communicator {
    /**
     * Send a buzz command to ESP32.
     * @param scheduleId  ID of the schedule that triggered this action.
     * @param command     Raw command string, e.g. "BUZZ:500:1"
     * @param callback    Async result: ACK or failure
     */
    void sendBuzzCommand(long scheduleId, String command, AckCallback callback);

    /**
     * Test connectivity to ESP32.
     * @param callback true if PING got ACK within timeout
     */
    void ping(AckCallback callback);

    interface AckCallback {
        void onAck(long scheduleId, String response);
        void onFailure(long scheduleId, String error);
    }
}
