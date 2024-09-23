package com.example.my_bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.gg.reader.api.dal.GClient;
import com.gg.reader.api.dal.HandlerTagEpcOver;
import com.gg.reader.api.protocol.gx.EnumG;
import com.gg.reader.api.protocol.gx.LogBaseEpcOver;
import com.gg.reader.api.protocol.gx.MsgBaseInventoryEpc;
import com.gg.reader.api.protocol.gx.MsgBaseSetPower;
import com.gg.reader.api.protocol.gx.MsgBaseStop;
import com.peripheral.ble.BleDevice;
import com.peripheral.ble.BleServiceCallback;
import com.peripheral.ble.BluetoothCentralManager;
import com.peripheral.ble.BluetoothCentralManagerCallback;
import com.peripheral.ble.BluetoothPeripheral;
import com.peripheral.ble.CharacteristicProperty;
import com.peripheral.ble.HciStatus;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;


import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BasicMessageChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.StandardMessageCodec;

/** RfidReaderPlugin */
public class MyBluetoothPlugin implements FlutterPlugin{
    private static final String FLUTTER_TO_ANDROID_CHANNEL = "flutter_and_android";
    private BasicMessageChannel<Object> flutter_channel;
    private Context applicationContext;
    private GClient client = new GClient();
    private BluetoothCentralManager central;
    private final String SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb";
    private BluetoothGatt mBluetoothGatt;
    private UUID read_UUID_chara;
    private UUID read_UUID_service;
    private UUID write_UUID_chara;
    private UUID write_UUID_service;
    private UUID notify_UUID_chara;
    private UUID notify_UUID_service;
    private UUID indicate_UUID_chara;
    private UUID indicate_UUID_service;
    List<String> message_list = new LinkedList<>();
    List<BluetoothPeripheral> peripherals = new LinkedList<>();
    
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
            map.put("connectMessage", "连接成功>>>" + peripheral.getName());
            flutter_channel.send(map);
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
    BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("epcAppearMessage", "服务连接成功开始扫描");
                    flutter_channel.send(map);
                    gatt.discoverServices();  // 开始扫描
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("epcAppearMessage", "服务连接断开");
                    flutter_channel.send(map);
                } else {
                    Map<String, Object> map = new HashMap<>();
                    map.put("epcAppearMessage", "服务连接失败");
                    flutter_channel.send(map);
                }
            }
        }
        
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            initServiceAndChara();
            mBluetoothGatt.setCharacteristicNotification(
                    mBluetoothGatt.getService(notify_UUID_chara).getCharacteristic(notify_UUID_chara),
                    true
            );
            Map<String, Object> map = new HashMap<>();
            map.put("epcAppearMessage", "通讯建立成功");
            flutter_channel.send(map);
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
    
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        applicationContext = flutterPluginBinding.getApplicationContext();
        flutter_channel = new BasicMessageChannel<>(
                flutterPluginBinding.getBinaryMessenger(),
                FLUTTER_TO_ANDROID_CHANNEL,
                StandardMessageCodec.INSTANCE
        );
        subscriberHandler();
        central = new BluetoothCentralManager(applicationContext, centralManagerCallback, new Handler(Looper.getMainLooper()));
        
        flutter_channel.setMessageHandler((message, reply) -> {
            Map<String, Object> arguments = (Map<String, Object>) message;
            if (arguments != null) {
                if (arguments.containsKey("startScanner")) {
                    if ((boolean) arguments.get("startScanner")) {
                        central.scanForPeripherals();
                        Map<String, String> map = new HashMap<>();
                        map.put("scanMessage", "开始扫描");
                        flutter_channel.send(map);
                    } 
                } else if (arguments.containsKey("bluetoothAddress")) {
                    String bluetooth_address = (String) arguments.get("bluetoothAddress");
                    for (BluetoothPeripheral peripheral: peripherals) {
                        if (peripheral.getAddress().equals(bluetooth_address)) {
                            BluetoothDevice bleDevice =
                                    BluetoothAdapter
                                    .getDefaultAdapter().getRemoteDevice(bluetooth_address);
                            bleDevice.connectGatt(applicationContext, false, gattCallback);
//                            device.setServiceCallback(new BleServiceCallback() {
//                                @Override
//                                public void onServicesDiscovered(BluetoothPeripheral peripheral) {
//                                    List<BluetoothGattService> services = peripheral.getServices();
//                                    Map<String, Object> maps = new HashMap<>();
//                                    List<String> uuids = new LinkedList<>();
//                                    String hh = "";
//                                    boolean hasall = false;
//                                    for (BluetoothGattService service : services) {
//                                        //示例"0000fff0-0000-1000-8000-00805f9b34fb"
////                                         49535343-fe7d-4ae5-8fa9-9fafd205e455
//                                        if (service.getUuid().toString().equals(SERVICE_UUID)) {
//                                            device.findCharacteristic(service);
//                                            uuids.add(service.getUuid().toString());
//                                            hh = "服务>>>"  + service.getUuid().toString() + uuids +
//                                                    "characteristic:" + device.getNotifyCharacteristic() +
//                                                    "reader:" + device.getReadCharacteristic() +
//                                                    "writer:" + device.getWriteCharacteristic() + service.getCharacteristics();
//                                        }
//                                    }
//                                    device.open("sks");
//                                    boolean notity = device.setNotify(true);
//                                    hh += notity;
//                                    maps.put("epcAppearOverMessage", hh + device.getNotifyCharacteristic());
//                                    flutter_channel.send(maps);
//                                }
//                            });
//                            client.openBleDevice(device);
                        }
                    }
                } else if (arguments.containsKey("stopScanner")) {
                    if ((boolean) arguments.get("stopScanner")) {
                        central.stopScan();
                        Map<String, String> map = new HashMap<>();
                        map.put("scanMessage", "停止扫描");
                        flutter_channel.send(map);
                    }
                } else if (arguments.containsKey("close_connect")) {
                    if ((boolean) arguments.get("close_connect")) {
                        client.close();
                        Map<String, String> map = new HashMap<>();
                        map.put("connectMessage", "连接已关闭");
                        flutter_channel.send(map);
                    }
                } else if (arguments.containsKey("startReader")) {
                    if ((boolean) arguments.get("startReader")) {
                        MsgBaseInventoryEpc msgBaseInventoryEpc = new MsgBaseInventoryEpc();
                        msgBaseInventoryEpc.setAntennaEnable(EnumG.AntennaNo_1);
                        msgBaseInventoryEpc.setInventoryMode(EnumG.InventoryMode_Inventory);
                        client.sendSynMsg(msgBaseInventoryEpc);
                        if (msgBaseInventoryEpc.getRtCode() == 0) {
                            Map<String, String> map = new HashMap<>();
                            map.put("readerOperationMssagee", "读卡操作成功" + client.getName());
                            flutter_channel.send(map);
                        } else {
                            Map<String, String> map = new HashMap<>();
                            map.put("readerOperationMessage", "读卡操作失败：" +client.getName()+ msgBaseInventoryEpc.getRtCode() + msgBaseInventoryEpc.getRtMsg());
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
        client.onTagEpcOver = new HandlerTagEpcOver() {
            @Override
            public void log(String s, LogBaseEpcOver logBaseEpcOver) {
                Log.e("HandlerTagEpcOver", logBaseEpcOver.getRtMsg());
                Map<String, Object> maps = new HashMap<>();
                maps.put("epcAppearOverMessage", "6C标签上报结束事件>>>" + logBaseEpcOver.getRtMsg());
                flutter_channel.send(maps);
            }
        };
    }
    
    private void initServiceAndChara(){
        List<BluetoothGattService> bluetoothGattServices= mBluetoothGatt.getServices();
        for (BluetoothGattService bluetoothGattService:bluetoothGattServices){
            List<BluetoothGattCharacteristic> characteristics=bluetoothGattService.getCharacteristics();
            for (BluetoothGattCharacteristic characteristic:characteristics){
                int charaProp = characteristic.getProperties();
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                    read_UUID_chara=characteristic.getUuid();
                    read_UUID_service=bluetoothGattService.getUuid();
//                    Log.e(TAG,"read_chara="+read_UUID_chara+"----read_service="+read_UUID_service);
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                    write_UUID_chara=characteristic.getUuid();
                    write_UUID_service=bluetoothGattService.getUuid();
//                    Log.e(TAG,"write_chara="+write_UUID_chara+"----write_service="+write_UUID_service);
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) {
                    write_UUID_chara=characteristic.getUuid();
                    write_UUID_service=bluetoothGattService.getUuid();
//                    Log.e(TAG,"write_chara="+write_UUID_chara+"----write_service="+write_UUID_service);
                
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    notify_UUID_chara=characteristic.getUuid();
                    notify_UUID_service=bluetoothGattService.getUuid();
//                    Log.e(TAG,"notify_chara="+notify_UUID_chara+"----notify_service="+notify_UUID_service);
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                    indicate_UUID_chara=characteristic.getUuid();
                    indicate_UUID_service=bluetoothGattService.getUuid();
//                    Log.e(TAG,"indicate_chara="+indicate_UUID_chara+"----indicate_service="+indicate_UUID_service);
                
                }
            }
        }
    }
    
    
    
}
