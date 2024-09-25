package com.example.my_bluetooth;

import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BasicMessageChannel;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.StandardMessageCodec;

/** RfidReaderPlugin */
public class MyBluetoothPlugin implements FlutterPlugin {
    private static final String FLUTTER_TO_ANDROID_CHANNEL = "flutter_and_android";
    private BasicMessageChannel<Object> flutter_channel;
    private Context applicationContext;
    private GClient client = new GClient();
    private BluetoothCentralManager central;

    List<String> message_list = new LinkedList<>();      // 设备名称和mac地址信息列表
    List<BluetoothPeripheral> peripherals = new LinkedList<>();   // 搜索到的设备列表

    BluetoothCentralManagerCallback centralManagerCallback = new BluetoothCentralManagerCallback() {
        @Override
        public void onDiscoveredPeripheral(BluetoothPeripheral peripheral, ScanResult scanResult) {
            if (!peripherals.contains(peripheral) && !peripheral.getName().isEmpty()) {
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
        Log.e("onAttachedToEngine", "onAttachedToEngine");
        applicationContext = flutterPluginBinding.getApplicationContext();
        
        flutter_channel = new BasicMessageChannel<>(
                flutterPluginBinding.getBinaryMessenger(),
                FLUTTER_TO_ANDROID_CHANNEL,
                StandardMessageCodec.INSTANCE
        );
        
      
        subscriberHandler();
        central = new BluetoothCentralManager(
                applicationContext,
                centralManagerCallback,
                new Handler(Looper.getMainLooper()));
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
                } else if (arguments.containsKey("bluetoothAddress")) {    // flutter端发送过来需要连接的设备mac地址
                    String bluetooth_address = (String) arguments.get("bluetoothAddress");

                    for (BluetoothPeripheral peripheral: peripherals) {   // 从搜索到的设备列表中匹配
                        if (peripheral.getAddress().equals(bluetooth_address)) {
                            BleDevice device = new BleDevice(central, peripheral);
                            device.setServiceCallback(new BleServiceCallback() {
                                @Override
                                public void onServicesDiscovered(BluetoothPeripheral peripheral) {
                                    List<BluetoothGattService> services = peripheral.getServices();
                                    for (BluetoothGattService service : services) {
                                        //示例"0000fff0-0000-1000-8000-00805f9b34fb"
                                        if (service.getUuid().toString().equals("0000fff0-0000-1000-8000-00805f9b34fb")) {
                                            device.findCharacteristic(service);
                                        }
                                    }
                                    device.setNotify(true);
                                }
                            });
                            client.openBleDevice(device);
                        }
                    }
                } else if (arguments.containsKey("stopScanner")) {
                    if ((boolean) arguments.get("stopScanner")) {
                        Log.e("扫描设备", "开始扫描");
                        Map<String, String> map = new HashMap<>();
                        map.put("scanMessage", "停止扫描");
                        flutter_channel.send(map);
                        central.stopScan();
                    }
                } else if (arguments.containsKey("close_connect")) {
                    if ((boolean) arguments.get("close_connect")) {
                        Log.e("主动关闭连接", "主动关闭设备连接");
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
                        client.sendSynMsg(msgBaseInventoryEpc, 50);
                        if (0x00 == msgBaseInventoryEpc.getRtCode()) {
                            Log.e("读卡", "操作成功");
                            Map<String, String> map = new HashMap<>();
                            map.put("readerOperationMssagee", "读卡操作成功");
                            flutter_channel.send(map);
                        } else {
                            Log.e("读卡", "操作失败");
                            Map<String, String> map = new HashMap<>();
                            map.put("readerOperationMessage", "读卡操作失败：" + msgBaseInventoryEpc.getRtCode() + msgBaseInventoryEpc.getRtMsg());
                            flutter_channel.send(map);
                        }
                    }
                } else if (arguments.containsKey("stopReader")) {
                    if ((boolean) arguments.get("stopReader")) {
                        MsgBaseStop msgBaseStop = new MsgBaseStop();
                        client.sendSynMsg(msgBaseStop, 50);
                        if (0x00 == msgBaseStop.getRtCode()) {
                            Log.e("取消读卡", "取消读卡操作成功");
                            Map<String, String> map = new HashMap<>();
                            map.put("readerOperationMessage", "取消读卡操作成功");
                            flutter_channel.send(map);
                        } else {
                            Log.e("取消读卡", "取消读卡操作失败");
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
                Map<String, Object> maps = new HashMap<>();
                maps.put("epcAppearMessage", "6C标签上报事件>>>" + logBaseEpcInfo.getEpc());
                flutter_channel.send(maps);
                System.out.println(maps);
                Log.e("readerEPC", logBaseEpcInfo.getEpc());
            }
        };
        client.onTagEpcOver = (s, logBaseEpcOver) -> {
            
            Map<String, Object> maps = new HashMap<>();
            maps.put("epcAppearOverMessage", "6C标签上报结束事件>>>" + logBaseEpcOver.getRtMsg());
            flutter_channel.send(maps);
            System.out.println(maps);
            Log.e("HandlerTagEpcOver", logBaseEpcOver.getRtMsg());
        };
    }

}
