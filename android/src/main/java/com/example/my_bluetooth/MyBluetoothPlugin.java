package com.example.my_bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.gg.reader.api.dal.GClient;
import com.gg.reader.api.dal.communication.BleBluetoothClient;
import com.gg.reader.api.dal.communication.BleClientCallback;
import com.gg.reader.api.dal.communication.BluetoothClient;
import com.gg.reader.api.dal.communication.BluetoothHandler;
import com.gg.reader.api.protocol.gx.EnumG;
import com.gg.reader.api.protocol.gx.MsgBaseInventoryEpc;
import com.gg.reader.api.protocol.gx.MsgBaseSetPower;
import com.gg.reader.api.protocol.gx.MsgBaseStop;
import com.peripheral.ble.BleDevice;
import com.peripheral.ble.BleServiceCallback;
import com.peripheral.ble.BluetoothCentralManager;
import com.peripheral.ble.BluetoothCentralManagerCallback;
import com.peripheral.ble.BluetoothPeripheral;
import com.peripheral.ble.HciStatus;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Hashtable;
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
    private BleBluetoothClient bleBluetoothClient;
    
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        applicationContext = flutterPluginBinding.getApplicationContext();
        bleBluetoothClient = new BleBluetoothClient(applicationContext);
        flutter_channel = new BasicMessageChannel<>(
                flutterPluginBinding.getBinaryMessenger(),
                FLUTTER_TO_ANDROID_CHANNEL,
                StandardMessageCodec.INSTANCE
        );
        List<String> message_list = new LinkedList<>();
        List<BluetoothDevice> devices = new LinkedList<>();
        bleBluetoothClient.scanCallBack = bluetoothDevice -> {
            if (!devices.contains(bluetoothDevice)) {
                devices.add(bluetoothDevice);
                String peripheral_name = bluetoothDevice.getName();
                String peripheral_address = bluetoothDevice.getAddress();
                message_list.add(peripheral_name + "#" + peripheral_address);
                Map<String, Object> map = new HashMap<>();
                map.put("bluetooth_list", message_list);
                flutter_channel.send(map);
            }
        };
        bleBluetoothClient.connectCallBack = new BleClientCallback.OnBlueConnectCallBack() {
            @Override
            public void onConnectSuccess() {
                Map<String, Object> map = new HashMap<>();
                map.put("connectMessage", "连接成功");
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
                flutter_channel.send(map);
            }
            @Override
            public void onConnectFailure() {
                Map<String, Object> map = new HashMap<>();
                map.put("connectMessage", "连接失败");
                flutter_channel.send(map);
            }
            
            @Override
            public void onDisconnect() {
                Map<String, Object> map = new HashMap<>();
                map.put("connectMessage", "断开连接");
                flutter_channel.send(map);
            }
        };
        
        flutter_channel.setMessageHandler((message, reply) -> {
            Map<String, Object> arguments = (Map<String, Object>) message;
            if (arguments != null) {
                if (arguments.containsKey("startScanner")) {
                    if ((boolean) arguments.get("startScanner")) {
                        bleBluetoothClient.scanBluetooth(true, 5000);
                        Map<String, String> map = new HashMap<>();
                        map.put("scanMessage", "开始扫描");
                        flutter_channel.send(map);
                    } 
                } else if (arguments.containsKey("bluetoothAddress")) {
                    String bluetooth_address = (String) arguments.get("bluetoothAddress");
                    if (client.openBleBluetooth(
                            bluetooth_address,
                            0,
                            bleBluetoothClient
                        )) {
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
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
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
                        client.close();
                    }
                } else if (arguments.containsKey("stopScanner")) {
                    if ((boolean) arguments.get("stopScanner")) {
                        bleBluetoothClient.stopScanBluetooth();
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
    
    
}
