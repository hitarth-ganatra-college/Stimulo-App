package com.stimulo.app.esp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StubEsp32Communicator implements Esp32Communicator {
    private static final String TAG = "BleEsp32Comm";

    private static final UUID SERVICE_UUID = UUID.fromString("7a1f7b10-9b9b-4a2f-9d22-7f7fd83a0001");
    private static final UUID RX_CHAR_UUID = UUID.fromString("7a1f7b10-9b9b-4a2f-9d22-7f7fd83a0002");
    private static final UUID TX_CHAR_UUID = UUID.fromString("7a1f7b10-9b9b-4a2f-9d22-7f7fd83a0003");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final long CONNECT_TIMEOUT_MS = 12000;
    private static final long ACK_TIMEOUT_MS = 6000;
    private static final int MAX_RETRIES = 3;

    private final Context appContext;
    private final String espMacAddress;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object stateLock = new Object();

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic rxCharacteristic;
    private BluetoothGattCharacteristic txCharacteristic;

    private volatile boolean connected = false;
    private volatile boolean connecting = false;
    private volatile boolean notificationsReady = false;
    private volatile boolean writeInProgress = false;

    private volatile String lastMessage = null;
    private volatile boolean waitingConnect = false;

    public StubEsp32Communicator(Context context, String espMacAddress) {
        this.appContext = context.getApplicationContext();
        this.espMacAddress = espMacAddress;
    }

    @Override
    public void sendBuzzCommand(long scheduleId, String command, AckCallback callback) {
        io.execute(() -> {
            if (!hasBlePermissions()) {
                postFail(callback, scheduleId, "Missing BLE permissions");
                return;
            }

            String expectedAckPrefix = "ACK:" + scheduleId;
            boolean ok = sendWithRetry(command, expectedAckPrefix);

            if (ok) {
                postAck(callback, scheduleId, lastMessage != null ? lastMessage : ("ACK:" + scheduleId));
            } else {
                postFail(callback, scheduleId, "No ACK from ESP32. Last=" + lastMessage);
            }
        });
    }

    @Override
    public void ping(AckCallback callback) {
        io.execute(() -> {
            if (!hasBlePermissions()) {
                postFail(callback, -1, "Missing BLE permissions");
                return;
            }

            boolean ok = sendWithRetry("PING", "ACK:PING");
            if (ok) {
                postAck(callback, -1, lastMessage != null ? lastMessage : "ACK:PING");
            } else {
                postFail(callback, -1, "PING failed. Last=" + lastMessage);
            }
        });
    }

    private boolean sendWithRetry(String payload, String expectedAckPrefix) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            Log.i(TAG, "Attempt " + attempt + " payload=" + payload);

            if (!ensureConnected()) {
                Log.w(TAG, "BLE connect failed attempt=" + attempt);
                sleep(300);
                continue;
            }

            lastMessage = null;

            if (!write(payload)) {
                Log.w(TAG, "BLE write failed attempt=" + attempt);
                sleep(200);
                continue;
            }

            boolean ack = waitForAck(expectedAckPrefix, ACK_TIMEOUT_MS);
            if (ack) return true;

            Log.w(TAG, "ACK timeout attempt=" + attempt + " expected=" + expectedAckPrefix + " last=" + lastMessage);
            sleep(200);
        }

        closeGatt();
        return false;
    }

    private boolean ensureConnected() {
        synchronized (stateLock) {
            if (connected && notificationsReady && gatt != null && rxCharacteristic != null && txCharacteristic != null) {
                return true;
            }
            if (!connecting) {
                connecting = true;
                waitingConnect = true;
                lastMessage = null;
            }
        }

        synchronized (stateLock) {
            if (gatt == null && connecting) {
                BluetoothManager bm = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
                if (bm == null) {
                    connecting = false;
                    waitingConnect = false;
                    return false;
                }
                BluetoothAdapter adapter = bm.getAdapter();
                if (adapter == null || !adapter.isEnabled()) {
                    connecting = false;
                    waitingConnect = false;
                    return false;
                }

                BluetoothDevice device;
                try {
                    device = adapter.getRemoteDevice(espMacAddress);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Invalid ESP MAC: " + espMacAddress, e);
                    connecting = false;
                    waitingConnect = false;
                    return false;
                }

                sleep(150);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
                    } else {
                        gatt = device.connectGatt(appContext, false, gattCallback);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "connectGatt exception", e);
                    gatt = null;
                    connecting = false;
                    waitingConnect = false;
                    return false;
                }
            }
        }

        long start = System.currentTimeMillis();
        while (waitingConnect && (System.currentTimeMillis() - start) < CONNECT_TIMEOUT_MS) {
            sleep(40);
        }

        synchronized (stateLock) {
            connecting = false;
            return connected && notificationsReady && gatt != null && rxCharacteristic != null && txCharacteristic != null;
        }
    }

    private boolean write(String msg) {
        synchronized (stateLock) {
            if (gatt == null || rxCharacteristic == null || !connected || !notificationsReady) return false;
            if (writeInProgress) return false;

            writeInProgress = true;
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                int res = gatt.writeCharacteristic(
                        rxCharacteristic,
                        data,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                );
                if (res != BluetoothGatt.GATT_SUCCESS) {
                    writeInProgress = false;
                    return false;
                }
                main.postDelayed(() -> writeInProgress = false, 120);
                return true;
            } else {
                rxCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                rxCharacteristic.setValue(data);
                boolean ok = gatt.writeCharacteristic(rxCharacteristic);
                if (!ok) {
                    writeInProgress = false;
                    return false;
                }
                main.postDelayed(() -> writeInProgress = false, 120);
                return true;
            }
        }
    }

    private boolean waitForAck(String expectedPrefix, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String msg = lastMessage;
            if (msg != null && msg.startsWith(expectedPrefix)) return true;
            sleep(20);
        }
        String msg = lastMessage;
        return msg != null && msg.startsWith(expectedPrefix);
    }

    private void closeGatt() {
        synchronized (stateLock) {
            connected = false;
            connecting = false;
            notificationsReady = false;
            writeInProgress = false;
            waitingConnect = false;
            rxCharacteristic = null;
            txCharacteristic = null;

            if (gatt != null) {
                try { gatt.disconnect(); } catch (Exception ignored) {}
                try { gatt.close(); } catch (Exception ignored) {}
            }
            gatt = null;
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "BLE connected");
                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BLE disconnected status=" + status);
                synchronized (stateLock) {
                    connected = false;
                    notificationsReady = false;
                    writeInProgress = false;
                    if (gatt == bluetoothGatt) {
                        try { bluetoothGatt.close(); } catch (Exception ignored) {}
                        gatt = null;
                    }
                }
                waitingConnect = false;
            } else {
                if (status != BluetoothGatt.GATT_SUCCESS) waitingConnect = false;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                waitingConnect = false;
                return;
            }

            BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
            if (service == null) {
                waitingConnect = false;
                return;
            }

            synchronized (stateLock) {
                rxCharacteristic = service.getCharacteristic(RX_CHAR_UUID);
                txCharacteristic = service.getCharacteristic(TX_CHAR_UUID);
                connected = (rxCharacteristic != null && txCharacteristic != null);
                notificationsReady = false;
            }

            if (!connected) {
                waitingConnect = false;
                return;
            }

            boolean notifSet = bluetoothGatt.setCharacteristicNotification(txCharacteristic, true);
            if (!notifSet) {
                waitingConnect = false;
                return;
            }

            BluetoothGattDescriptor cccd = txCharacteristic.getDescriptor(CCCD_UUID);
            if (cccd != null) {
                cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                boolean initiated = bluetoothGatt.writeDescriptor(cccd);
                if (!initiated) waitingConnect = false;
            } else {
                synchronized (stateLock) { notificationsReady = true; }
                waitingConnect = false;
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt bluetoothGatt, BluetoothGattDescriptor descriptor, int status) {
            if (CCCD_UUID.equals(descriptor.getUuid())) {
                synchronized (stateLock) {
                    notificationsReady = (status == BluetoothGatt.GATT_SUCCESS);
                }
                waitingConnect = false;
                Log.i(TAG, "CCCD write status=" + status + " ready=" + notificationsReady);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic, int status) {
            writeInProgress = false;
            Log.d(TAG, "onCharacteristicWrite status=" + status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic) {
            if (TX_CHAR_UUID.equals(characteristic.getUuid())) {
                String msg = new String(characteristic.getValue(), StandardCharsets.UTF_8).trim();
                Log.i(TAG, "BLE notify: " + msg);
                lastMessage = msg;
            }
        }
    };

    private boolean hasBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void postAck(AckCallback cb, long id, String msg) {
        if (cb == null) return;
        main.post(() -> cb.onAck(id, msg));
    }

    private void postFail(AckCallback cb, long id, String err) {
        if (cb == null) return;
        main.post(() -> cb.onFailure(id, err));
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}