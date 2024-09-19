package com.example.my_bluetooth;

import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.gg.reader.api.dal.GClient;
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
        BluetoothCentralManagerCallback centralManagerCallback = new BluetoothCentralManagerCallback() {
            @Override
            public void onDiscoveredPeripheral(BluetoothPeripheral peripheral, ScanResult scanResult) {
                peripherals.add(peripheral);
                String peripheral_name = peripheral.getName();
                String peripheral_address = peripheral.getAddress();
                message_list.add(peripheral_name + "#" + peripheral_address);
                Map<String, Object> map = new HashMap<>();
                map.put("bluetooth_list", message_list);
                flutter_channel.send(map);
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
        central = new BluetoothCentralManager(applicationContext, centralManagerCallback, new Handler(Looper.getMainLooper()));
        client.onTagEpcLog = (s, logBaseEpcInfo) -> {
            if (logBaseEpcInfo.getResult() == 0) {
                Log.e("epc", logBaseEpcInfo.getEpc());
                Map<String, Object> map = new HashMap<>();
                map.put("epcAppearMessage", "6C标签上报事件>>>" + logBaseEpcInfo.getEpc());
                flutter_channel.send(map);
            }
        };
        client.onTagEpcOver = (s, logBaseEpcOver) -> {
            Log.e("HandlerTagEpcOver", logBaseEpcOver.getRtMsg());
            Map<String, Object> map = new HashMap<>();
            map.put("epcAppearOverMessage", "6C标签上报结束事件>>>" + logBaseEpcOver.getRtMsg());
            flutter_channel.send(map);
        };
        flutter_channel.setMessageHandler((message, reply) -> {
            Map<String, Object> arguments = (Map<String, Object>) message;
            if (arguments != null) {
                if (arguments.containsKey("startScanner")) {
                    if ((boolean) arguments.get("startScanner")) {
                        central.scanForPeripherals();
                        Map<String, String> map = new HashMap<>();
                        map.put("scanMessage", "开始扫描");
                        flutter_channel.send(map);
                    } else {
                        central.stopScan();
                        Map<String, String> map = new HashMap<>();
                        map.put("scanMessage", "停止扫描");
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
                }
            }
        });
    }
    
    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    
    }
    
    
}
