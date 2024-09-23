package com.example.my_bluetooth;

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


import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BasicMessageChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.StandardMessageCodec;

/** RfidReaderPlugin */
public class MyBluetoothPlugin implements FlutterPlugin,  MethodCallHandler {
    private static final String FLUTTER_TO_ANDROID_CHANNEL = "flutter_and_android";
    private BasicMessageChannel<Object> flutter_channel;
    private Context applicationContext;
    private GClient client = new GClient();
    private BluetoothCentralManager central;
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
                            central.stopScan();
                            BleDevice device = new BleDevice(central, peripheral);
                            device.setServiceCallback(new BleServiceCallback() {
                                @Override
                                public void onServicesDiscovered(BluetoothPeripheral peripheral) {
                                    List<BluetoothGattService> services = peripheral.getServices();
                                    Map<String, Object> maps = new HashMap<>();
                                    List<String> uuids = new LinkedList<>();
                                    String hh = "";
                                    boolean hasall = false;
                                    for (BluetoothGattService service : services) {
                                        //示例"0000fff0-0000-1000-8000-00805f9b34fb"
//                                         49535343-fe7d-4ae5-8fa9-9fafd205e455
                                        if (service.getUuid().toString().equals("0000fff0-0000-1000-8000-00805f9b34fb")) {
                                            device.findCharacteristic(service);
                                            uuids.add(service.getUuid().toString());
                                            hh = "服务>>>"  + service.getUuid().toString() + uuids +
                                                    "characteristic:" + device.getNotifyCharacteristic() +
                                                    "reader:" + device.getReadCharacteristic() +
                                                    "writer:" + device.getWriteCharacteristic() + service.getCharacteristics();
                                        }
                                    }
                                    boolean notity = device.setNotify(true);
                                    hh += notity;
                                    maps.put("epcAppearOverMessage", hh + device.getNotifyCharacteristic());
                                    flutter_channel.send(maps);
                                }
                            });
                            client.openBleDevice(device);
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
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (call.method.equals("getPlatformVersion")) {
            result.success("Android " + android.os.Build.VERSION.RELEASE);
        } else {
            result.notImplemented();
        }
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
    
    
}
