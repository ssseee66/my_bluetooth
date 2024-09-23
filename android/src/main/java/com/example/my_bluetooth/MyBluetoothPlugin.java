package com.example.my_bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.gg.reader.api.dal.GClient;
import com.gg.reader.api.protocol.gx.EnumG;
import com.gg.reader.api.protocol.gx.MsgBaseInventoryEpc;
import com.gg.reader.api.protocol.gx.MsgBaseStop;
import com.peripheral.ble.BleDevice;
import com.peripheral.ble.BleServiceCallback;
import com.peripheral.ble.BluetoothCentralManager;
import com.peripheral.ble.BluetoothCentralManagerCallback;
import com.peripheral.ble.BluetoothPeripheral;
import com.peripheral.ble.HciStatus;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BasicMessageChannel;
import io.flutter.plugin.common.StandardMessageCodec;

/** RfidReaderPlugin */
public class MyBluetoothPlugin implements FlutterPlugin {
    private static final String FLUTTER_TO_ANDROID_CHANNEL = "flutter_and_android";
    private BasicMessageChannel<Object> flutter_channel;
    private Context applicationContext;
    private GClient client = new GClient();
    private BluetoothCentralManager central;
    
    private UUID SERVER_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805F9B34FB");
    private UUID NOTIFY_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805F9B34FB");
    private UUID WRITE_UUID = UUID.fromString("0000fff2-0000-1000-8000-00805F9B34FB");
    private static final UUID DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattService bluetoothService;
    private BluetoothGattCharacteristic mWriteCharacteristic;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothAdapter adapter;
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        applicationContext = flutterPluginBinding.getApplicationContext();
        flutter_channel = new BasicMessageChannel<>(
                flutterPluginBinding.getBinaryMessenger(),
                FLUTTER_TO_ANDROID_CHANNEL,
                StandardMessageCodec.INSTANCE
        );
        List<String> message_list = new LinkedList<>();
        List<BluetoothPeripheral> peripherals = new LinkedList<>();
        
        BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Map<String, String> map = new HashMap<>();
                    map.put("epcAppearMesage", "服务连接成功");
                    flutter_channel.send(map);
                    gatt.discoverServices();
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Map<String, String> map = new HashMap<>();
                    map.put("epcAppearMesage", "服务连接关闭");
                    flutter_channel.send(map);
                }
            }
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Map<String, String> map = new HashMap<>();
                    map.put("epcAppearMesage", "通讯服务连接成功");
                    flutter_channel.send(map);
                    enableTxNotification();
                } else {
                    Map<String, String> map = new HashMap<>();
                    map.put("epcAppearMesage", "通讯服务连接失败");
                    flutter_channel.send(map);
                }
            }
            @Override
            public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
                super.onCharacteristicRead(gatt, characteristic, value, status);
            }
            
            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
            }
        };
        BluetoothCentralManagerCallback centralManagerCallback = new BluetoothCentralManagerCallback() {
            @Override
            public void onDiscoveredPeripheral(BluetoothPeripheral peripheral, ScanResult scanResult) {
                if (!peripherals.contains(peripheral)) {
                    peripherals.add(peripheral);
                    String peripheral_name = peripheral.getName();
                    String peripheral_address = peripheral.getAddress();
                    message_list.add(peripheral_name + "#" + peripheral_address);
                    Map<String, Object> map = new HashMap<>();
                    map.put("bluetooth_list", message_list);
                    flutter_channel.send(map);
                }
            }
            @Override
            public void onConnectedPeripheral(BluetoothPeripheral peripheral) {
                Log.e(peripheral.getName(), "连接成功");
                Map<String, Object> map = new HashMap<>();
                client.setSendHeartBeat(true);
                map.put("connectMessage", "连接成功>>>" + peripheral.getName());
                flutter_channel.send(map);
                adapter = BluetoothAdapter.getDefaultAdapter();
                BluetoothDevice device = adapter.getRemoteDevice(peripheral.getAddress());
                if (Build.VERSION.SDK_INT >= 26) {
                    bluetoothGatt = device.connectGatt(applicationContext, false, bluetoothGattCallback, 2, 1);
                } else if (Build.VERSION.SDK_INT >= 23) {
                    bluetoothGatt = device.connectGatt(applicationContext, false, bluetoothGattCallback, 2);
                } else {
                    bluetoothGatt = device.connectGatt(applicationContext, false, bluetoothGattCallback);
                }
            }
            @Override
            public void onConnectionFailed(BluetoothPeripheral peripheral, HciStatus status) {
                Log.e(peripheral.getName(), "连接失败");
                Map<String, Object> map = new HashMap<>();
                map.put("connectMessage", "连接失败>>>" + peripheral.getName());
                flutter_channel.send(map);
            }
            @Override
            public void onDisconnectedPeripheral(BluetoothPeripheral peripheral, HciStatus status) {
                Log.e(peripheral.getName(), "断开连接");
                Map<String, Object> map = new HashMap<>();
                map.put("connectMessage", "断开连接>>>" + peripheral.getName());
                flutter_channel.send(map);
            }
        };
        subscriberHandler();
        central = new BluetoothCentralManager(applicationContext, centralManagerCallback, new Handler(Looper.getMainLooper()));
        
        flutter_channel.setMessageHandler((message, reply) -> {
            Map<String, Object> arguments = (Map<String, Object>) message;
            if (arguments != null) {
                if (arguments.containsKey("startScanner")) {
                    if ((boolean) arguments.get("startScanner")) {
                        Map<String, String> map = new HashMap<>();
                        map.put("scanMessage", "开始扫描");
                        flutter_channel.send(map);
                        central.scanForPeripherals();
                    }
                } else if (arguments.containsKey("bluetoothAddress")) {
                    String bluetooth_address = (String) arguments.get("bluetoothAddress");
                    for (BluetoothPeripheral peripheral: peripherals) {
                        if (peripheral.getAddress().equals(bluetooth_address)) {
                            BleDevice bleDevice = new BleDevice(central, peripheral);
                            client.openBleDevice(bleDevice);
//                            device.setServiceCallback(new BleServiceCallback() {
//                                @Override
//                                public void onServicesDiscovered(BluetoothPeripheral peripheral) {
//                                    List<BluetoothGattService> services = peripheral.getServices();
//                                    for (BluetoothGattService service : services) {
//                                        //示例"0000fff0-0000-1000-8000-00805f9b34fb"
//                                        if (service.getUuid().toString().equals(SERVER_UUID.toString()) ) {
//                                            device.findCharacteristic(service);
//                                        }
//                                    }
//                                    device.setNotify(true);
//                                }
//                            });
//                            client.openBleDevice(device);
                        }
                    }
                } else if (arguments.containsKey("stopScanner")) {
                    if ((boolean) arguments.get("stopScanner")) {
                        Map<String, String> map = new HashMap<>();
                        map.put("scanMessage", "停止扫描");
                        flutter_channel.send(map);
                        central.stopScan();
                    }
                } else if (arguments.containsKey("close_connect")) {
                    if ((boolean) arguments.get("close_connect")) {
                        Map<String, String> map = new HashMap<>();
                        map.put("connectMessage", "连接已关闭");
                        flutter_channel.send(map);
                        client.close();
                    }
                } else if (arguments.containsKey("startReader")) {
                    if ((boolean) arguments.get("startReader")) {
                        MsgBaseInventoryEpc msgBaseInventoryEpc = new MsgBaseInventoryEpc();
                        msgBaseInventoryEpc.setAntennaEnable(EnumG.AntennaNo_1);
                        msgBaseInventoryEpc.setInventoryMode(EnumG.InventoryMode_Inventory);
                        client.sendSynMsg(msgBaseInventoryEpc);
                        if (0x00 == msgBaseInventoryEpc.getRtCode()) {
                            Map<String, String> map = new HashMap<>();
                            map.put("readerOperationMssagee", "读卡操作成功");
                            flutter_channel.send(map);
                        } else {
                            Map<String, String> map = new HashMap<>();
                            map.put("readerOperationMessage", "读卡操作失败：" + msgBaseInventoryEpc.getRtCode() + msgBaseInventoryEpc.getRtMsg());
                            flutter_channel.send(map);
                        }
                    }
                } else if (arguments.containsKey("stopReader")) {
                    if ((boolean) arguments.get("stopReader")) {
                        MsgBaseStop msgBaseStop = new MsgBaseStop();
                        client.sendSynMsg(msgBaseStop);
                        if (0x00 == msgBaseStop.getRtCode()) {
                            Map<String, String> map = new HashMap<>();
                            map.put("readerOperationMessage", "取消读卡操作成功");
                            flutter_channel.send(map);
                        } else {
                            Map<String, String> map = new HashMap<>();
                            map.put("readerOperationMessage", "取消读卡操作失败");
                            flutter_channel.send(map);
                        }
                    }
                }
            }
        });
    }
    
    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {

    }
    
    private void subscriberHandler() {
        client.onTagEpcLog = (s, logBaseEpcInfo) -> {
            if (logBaseEpcInfo.getResult() == 0) {
                Log.e("epc", logBaseEpcInfo.getEpc());
                Map<String, Object> maps = new HashMap<>();
                maps.put("epcAppearMessage", "6C标签上报事件>>>" + logBaseEpcInfo.getEpc());
                flutter_channel.send(maps);
            }
        };
        client.onTagEpcOver = (s, logBaseEpcOver) -> {
            Log.e("HandlerTagEpcOver", logBaseEpcOver.getRtMsg());
            Map<String, Object> maps = new HashMap<>();
            maps.put("epcAppearOverMessage", "6C标签上报结束事件>>>" + logBaseEpcOver.getRtMsg());
            flutter_channel.send(maps);
        };
    }
    public void enableTxNotification() {
        BluetoothGattService service = bluetoothGatt.getService(SERVER_UUID);
        mWriteCharacteristic = service.getCharacteristic(WRITE_UUID);
        mNotifyCharacteristic = service.getCharacteristic(NOTIFY_UUID);
        bluetoothGatt.setCharacteristicNotification(mNotifyCharacteristic, true);
        BluetoothGattDescriptor mDescriptor = mNotifyCharacteristic.getDescriptor(DESCRIPTOR);
        if (mDescriptor != null) {
            if ((mNotifyCharacteristic.getProperties() & 16) > 0) {
                mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            } else if ((mNotifyCharacteristic.getProperties() & 32) > 0) {
                mDescriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            }
        }
        
        this.bluetoothGatt.writeDescriptor(mDescriptor);
    }
    
}
