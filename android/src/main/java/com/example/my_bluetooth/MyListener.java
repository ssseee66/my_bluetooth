package com.example.my_bluetooth;

import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.gg.reader.api.dal.GClient;
import com.gg.reader.api.dal.HandlerDebugLog;
import com.gg.reader.api.protocol.gx.EnumG;
import com.gg.reader.api.protocol.gx.MsgBaseGetCapabilities;
import com.gg.reader.api.protocol.gx.MsgBaseGetPower;
import com.gg.reader.api.protocol.gx.MsgBaseInventoryEpc;
import com.gg.reader.api.protocol.gx.MsgBaseSetPower;
import com.peripheral.ble.BleDevice;
import com.peripheral.ble.BleServiceCallback;
import com.peripheral.ble.BluetoothCentralManager;
import com.peripheral.ble.BluetoothCentralManagerCallback;
import com.peripheral.ble.BluetoothPeripheral;
import com.peripheral.ble.HciStatus;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.flutter.plugin.common.BasicMessageChannel;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.StandardMessageCodec;

public class MyListener {
    private final BasicMessageChannel<Object> message_channel;
    private final UUID SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private final GClient client = new GClient();
    private Map<String, Object> arguments;
    private final BluetoothCentralManager central;
    private Long ANTENNA_NUM = 0L;
    private int CURRENT_ANTENNA_NUM = 0;
    private final Map<String, Object> message_map = new HashMap<>();
    private boolean APPEAR_OVER = false;
    
    List<String> message_list = new LinkedList<>();      // 设备名称和mac地址信息列表
    List<BluetoothPeripheral> peripherals = new LinkedList<>();   // 搜索到的设备列表
    List<String> epcMessages = new LinkedList<>();
    
    BluetoothCentralManagerCallback centralManagerCallback = new BluetoothCentralManagerCallback() {
        @Override
        public void onDiscoveredPeripheral(BluetoothPeripheral peripheral, ScanResult scanResult) {
            if (!peripherals.contains(peripheral) && !peripheral.getName().isEmpty()) {
                Log.e("peripheralAddress", peripheral.getAddress());
                peripherals.add(peripheral);
                String peripheral_name = peripheral.getName();
                String peripheral_address = peripheral.getAddress();
                message_list.add(peripheral_name + "#" + peripheral_address);
                message_map.clear();
                message_map.put("bluetooth_list", message_list);
                message_channel.send(message_map);
            }
        }
        @Override
        public void onConnectedPeripheral(BluetoothPeripheral peripheral) {
            Log.e(peripheral.getName(), "连接成功" + peripheral.getAddress());
            message_map.clear();
            message_map.put("connectMessage", "连接成功>>>" + peripheral.getName());
            message_channel.send(message_map);
        }
        @Override
        public void onConnectionFailed(BluetoothPeripheral peripheral, HciStatus status) {
            Log.e(peripheral.getName(), "连接失败");
            message_map.clear();
            message_map.put("connectMessage", "连接失败>>>" + peripheral.getName());
            message_channel.send(message_map);
        }
        @Override
        public void onDisconnectedPeripheral(BluetoothPeripheral peripheral, HciStatus status) {
            Log.e(peripheral.getName(), "断开连接");
            message_map.clear();
            message_map.put("connectMessage", "断开连接>>>" + peripheral.getName());
            message_channel.send(message_map);
        }
    };

    MyListener(String channelName, Context applicationContext, BinaryMessenger binaryMessenger) {
        
        message_channel = new BasicMessageChannel<>(
                binaryMessenger,
                channelName,
                StandardMessageCodec.INSTANCE
        );
        Log.e("listener_channel_name", channelName);
        
        subscriberHandler();
        central = new BluetoothCentralManager(
                applicationContext,
                centralManagerCallback,
                new Handler(Looper.getMainLooper()));
        message_channel.setMessageHandler((message, reply) -> {
            arguments = castMap(message, String.class, Object.class);
            if (arguments == null) return;
            executeOperation(getCurrentKey());
        });
    }
    
    private void setANTENNA_NUM() {
        switch (CURRENT_ANTENNA_NUM) {
            case 1:
                ANTENNA_NUM = EnumG.AntennaNo_1;
                break;
            case 2:
                ANTENNA_NUM = EnumG.AntennaNo_2;
                break;
            case 3:
                ANTENNA_NUM = EnumG.AntennaNo_3;
                break;
            case 4:
                ANTENNA_NUM = EnumG.AntennaNo_4;
                break;
            default:
                ANTENNA_NUM = 0L;
                break;
        }
    }
    
    private String getCurrentKey() {
        String key = null;
        if (arguments.containsKey("startScanner")) key = "startScanner";
        else if (arguments.containsKey("stopScanner")) key = "stopScanner";
        else if (arguments.containsKey("bluetoothAddress")) key = "connect";
        else if (arguments.containsKey("closeConnect")) key = "closeConnect";
        else if (arguments.containsKey("startReader")) key = "startReader";
        else if (arguments.containsKey("startReaderEpc")) key = "startReaderEpc";
        else if (arguments.containsKey("setAntennaNum")) key = "setAntennaNum";
        else if (arguments.containsKey("setAntennaPower")) key = "setAntennaPower";
        else if (arguments.containsKey("queryRfidCapacity")) key = "queryRfidCapacity";
        return key;
    }
    
    private void executeOperation(@NonNull String key) {
        switch (key) {
            case "startScanner":
                scanBleDevice(key);
                break;
            case "stopScanner":
                stopScanBleDevice(key);
                break;
            case "connect":
                connectBleDevice(key);
                break;
            case "closeConnect":
                closeBleDeviceConnect(key);
                break;
            case "startReader":
                startReader(key);
                break;
            case "startReaderEpc":
                startReaderEpc(key);
                break;
            case "setAntennaNum":
                setAntennaNum(key);
                break;
            case "setAntennaPower":
                setAntennaPower(key);
                break;
            case "queryRfidCapacity":
                queryRfidCapacity(key);
                break;
            default:
                break;
        }
    }
    
    @NonNull
    private static String setRfidMessage(
            @NonNull MsgBaseGetPower msgBaseGetPower,
            MsgBaseGetCapabilities msgBaseGetCapabilities
    ) {
        StringBuilder current_power = new StringBuilder("current:");
        if (msgBaseGetPower.getRtCode() == 0) {
            Hashtable<Integer, Integer> powers = msgBaseGetPower.getDicPower();
            for (Map.Entry<Integer, Integer> entry : powers.entrySet()) {
                current_power
                        .append(entry.getKey())
                        .append("#")
                        .append(entry.getValue())
                        .append("@");
            }
        }
        return  current_power + "&" +
                "max_power:" + msgBaseGetCapabilities.getMaxPower() + "&" +
                "min_power:" + msgBaseGetCapabilities.getMinPower() + "&" +
                "antenna_count:" + msgBaseGetCapabilities.getAntennaCount() + "&";
    }
    
    private int getCurrentAntennaNum(Long antennaNum) {
        if (antennaNum == EnumG.AntennaNo_1) {
            return 1;
        } else if (antennaNum == EnumG.AntennaNo_2) {
            return 2;
        } else if (antennaNum == EnumG.AntennaNo_3) {
            return 3;
        } else if (antennaNum == EnumG.AntennaNo_4) {
            return 4;
        }
        return 0;
    }
    private void subscriberHandler() {
        client.onTagEpcLog = (s, logBaseEpcInfo) -> {
            if (logBaseEpcInfo.getResult() == 0) {
                Log.e("readerEPC", logBaseEpcInfo.getEpc());
                epcMessages.add(logBaseEpcInfo.getEpc());
            }
        };
        client.onTagEpcOver = (s, logBaseEpcOver) -> {
            Log.e("HandlerTagEpcOver", logBaseEpcOver.getRtMsg());
            // send();
            Log.e("epcAppearOver", epcMessages.toString());
            APPEAR_OVER = true;
        };
        
        client.debugLog = new HandlerDebugLog() {
            public void sendDebugLog(String msg) {
                Log.e("sendDebugLog",msg);
            }
            
            public void receiveDebugLog(String msg) {
                Log.e("receiveDebugLog",msg);
            }
        };
    }
    
    public static <K, V> Map<K, V> castMap(Object obj, Class<K> key, Class<V> value) {
        Map<K, V> map = new HashMap<>();
        if (obj instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                map.put(key.cast(entry.getKey()), value.cast(entry.getValue()));
            }
            return map;
        }
        return null;
    }
    
    private void scanBleDevice(String key) {
        Object value = arguments.get(key);
        if (value == null) return;
        if (!(boolean) value) return;
        peripherals.clear();
        message_list.clear();
        central.scanForPeripherals();
        message_map.clear();
        message_map.put("scanMessage", "开始扫描");
        message_channel.send(message_map);
    }
    private void stopScanBleDevice(String key) {
        Object value = arguments.get(key);
        if (value == null) return;
        if (!(boolean) value) return;
        Log.e("扫描设备", "停止扫描");
        message_map.clear();
        message_map.put("scanMessage", "停止扫描");
        message_channel.send(message_map);
        central.stopScan();
    }
    private void connectBleDevice(String key) {
        String bluetooth_address = (String) arguments.get(key);
        BluetoothPeripheral peripheral = central.getPeripheral(bluetooth_address);
        BleDevice device = setBleDevice(peripheral);
        client.openBleDevice(device);
    }
    @NonNull
    private BleDevice setBleDevice(BluetoothPeripheral peripheral) {
        BleDevice device = new BleDevice(central, peripheral);
        device.setServiceCallback(new BleServiceCallback() {
            @Override
            public void onServicesDiscovered(BluetoothPeripheral peripheral) {
                List<BluetoothGattService> services = peripheral.getServices();
                for (BluetoothGattService service : services) {
                    //示例"0000fff0-0000-1000-8000-00805f9b34fb"
                    if (service.getUuid().toString().equals(SERVICE_UUID.toString())) {
                        device.findCharacteristic(service);
                    }
                }
                device.setNotify(true);
            }
        });
        return device;
    }
    private void closeBleDeviceConnect(String key) {
        Object value = arguments.get(key);
        if (value == null) return;
        if (!(boolean) value) return;
        Log.e("主动关闭连接", "主动关闭设备连接");
        message_map.clear();
        message_map.put("connectMessage", "连接已关闭");
        message_channel.send(message_map);
        client.close();
        epcMessages.clear();
    }
    private void startReader(String key) {
        Object value = arguments.get(key);
        if (value == null) return;
        if (!(boolean) value) return;
        if (ANTENNA_NUM == 0L) {
            message_map.clear();
            message_map.put("readerOperationMessage", "未配置天线端口，请先配置天线端口");
            message_channel.send(message_map);
            return;
        }
        MsgBaseInventoryEpc msgBaseInventoryEpc = new MsgBaseInventoryEpc();
        msgBaseInventoryEpc.setAntennaEnable(ANTENNA_NUM);
        msgBaseInventoryEpc.setInventoryMode(EnumG.InventoryMode_Single);
        client.sendSynMsg(msgBaseInventoryEpc);
        boolean operationSuccess = false;
        if (0x00 == msgBaseInventoryEpc.getRtCode()) {
            // Log.e("读卡", "操作成功");
            Log.e("读卡", "操作成功");
            operationSuccess = true;
        } else {
            // Log.e("读卡", "操作失败");
            message_map.clear();
            message_map.put("readerOperationMessage",
                    "读卡操作失败：" +
                            msgBaseInventoryEpc.getRtCode() +
                            msgBaseInventoryEpc.getRtMsg());
            message_channel.send(message_map);
            Log.e("读卡", "操作失败");
        }
        // 搞不懂为什么要在外层进行通讯才行，在里面发送的话会发送不了
        // 并且通讯方法只能在主线程中调用，无法通过创建新线程处理
        if (!operationSuccess) return;
        Log.e("读卡操作", "读卡操作成功");
        message_map.clear();
        CURRENT_ANTENNA_NUM = getCurrentAntennaNum(msgBaseInventoryEpc.getAntennaEnable());
        message_map.put("readerOperationMessage",
                "读卡操作成功,数据端口：" + CURRENT_ANTENNA_NUM);
        message_channel.send(message_map);
    }
    private void startReaderEpc(String key) {
        Object value = arguments.get(key);
        if (value == null) return;
        if (!(boolean) value) return;
        Log.e("start_reader_epc", "开始读取数据");
        if (APPEAR_OVER) {
            Log.e("client", client + "");
            message_map.clear();
            epcMessages.add("数据端口:" + CURRENT_ANTENNA_NUM);
            message_map.put("epcMessages", epcMessages);
            Log.e("epcMessages", "" + message_map);
            message_channel.send(message_map);
            epcMessages.clear();
            APPEAR_OVER = false;
        } else {
            message_map.clear();
            List<String> message_list = new LinkedList<>();
            message_list.add("未上报结束");
            message_map.put("epcMessages", message_list);
            message_channel.send(message_map);
            Log.e("appear_over_not", "未上报结束");
        }
    }
    private void setAntennaNum(String key) {
        Object value = arguments.get(key);
        if (value == null) return;
        CURRENT_ANTENNA_NUM = (int) value;
        Log.e("antenna_num", CURRENT_ANTENNA_NUM + "");
        setANTENNA_NUM();
        if (ANTENNA_NUM == 0L) return;
        message_map.clear();
        message_map.put("AntennaNumMessage", "天线设置成功");
        message_channel.send(message_map);
    }
    private void setAntennaPower(String key) {
        MsgBaseSetPower msgBaseSetPower = new MsgBaseSetPower();
        String antenna_message =
                (String) arguments.get(key);
        if (antenna_message == null) return;
        Log.e("power", antenna_message);
        Hashtable<Integer, Integer> hashtable = new Hashtable<>();
        for (String antenna : antenna_message.split("&", -1)) {
            String[] messages = antenna.split("#", -1);
            Integer num = Integer.parseInt(messages[0]);
            Integer power = Integer.parseInt(messages[1]);
            hashtable.put(num, power);
        }
        msgBaseSetPower.setDicPower(hashtable);
        client.sendSynMsg(msgBaseSetPower);
        if (msgBaseSetPower.getRtCode() == 0) {
            Log.e("设置天线功率", "设置成功");
            MsgBaseGetPower msgBaseGetPower = new MsgBaseGetPower();
            client.sendSynMsg(msgBaseGetPower);
            if (msgBaseGetPower.getRtCode() == 0) {
                message_map.clear();
                message_map.put("AntennaNumMessage",
                        "天线功率设置成功:" + msgBaseGetPower.getDicPower());
                message_channel.send(message_map);
            }
        } else {
            Log.e("设置天线功率", "设置失败");
            message_map.clear();
            message_map.put("AntennaNumMessage", "天线功率设置失败");
            message_channel.send(message_map);
        }
    }
    private void queryRfidCapacity(String key) {
        Object value = arguments.get(key);
        if (value == null) return;
        if (!(boolean) value) return;
        MsgBaseGetCapabilities msgBaseGetCapabilities = new MsgBaseGetCapabilities();
        Log.e("start_query", "开始查询");
        client.sendSynMsg(msgBaseGetCapabilities);
        if (msgBaseGetCapabilities.getRtCode() == 0X00) {
            message_map.clear();
            MsgBaseGetPower msgBaseGetPower = new MsgBaseGetPower();
            client.sendSynMsg(msgBaseGetPower);
            String rfid_message = setRfidMessage(msgBaseGetPower, msgBaseGetCapabilities);
            Log.e("rfid_message", rfid_message);
            message_map.put("rfidCapacityMessage", rfid_message);
            message_channel.send(message_map);
        } else {
            message_map.clear();
            Log.e("rfid_message", "查询失败:" + msgBaseGetCapabilities.getRtCode());
            message_map.put("rfidCapacityMessage",
                    "查询失败:" + msgBaseGetCapabilities.getRtCode());
            message_channel.send(message_map);
        }
    }
}
