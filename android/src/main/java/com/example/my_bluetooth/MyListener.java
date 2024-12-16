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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

import io.flutter.plugin.common.BasicMessageChannel;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.StandardMessageCodec;

public class MyListener {
    private final BasicMessageChannel<Object> message_channel;
    private final UUID SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private final GClient client = new GClient();
    private Map<String, Object> arguments;
    private final BluetoothCentralManager central;    //  蓝牙管理类
    private Long ANTENNA_NUM = 0L;
    private int CURRENT_ANTENNA_NUM = 0;
    private final Map<String, Object> message_map = new HashMap<>();
    private final Map<String, Consumer<String>> action_map = new HashMap<>();
    private boolean APPEAR_OVER = false;
    
    List<String> message_list = new LinkedList<>();      // 设备名称和mac地址信息列表
    List<BluetoothPeripheral> peripherals = new LinkedList<>();   // 搜索到的设备列表
    List<String> epcMessages = new LinkedList<>();
    
    //  蓝牙适配器相关回调函数，这里只是重写了扫描附近蓝牙设备、以及蓝牙连接相关的方法
    BluetoothCentralManagerCallback centralManagerCallback = new BluetoothCentralManagerCallback() {
        @Override    //  蓝牙管理类实例对象调用扫描蓝牙设备方法后会调用此方法
        public void onDiscoveredPeripheral(BluetoothPeripheral peripheral, ScanResult scanResult) {
            //  将蓝牙设备进行过滤，避免蓝牙设备重复、以及蓝牙设备名称为空
            if (!peripherals.contains(peripheral) && !peripheral.getName().isEmpty()) {
                Log.e("peripheralAddress", peripheral.getAddress());
                peripherals.add(peripheral);
                String peripheral_name = peripheral.getName();
                String peripheral_address = peripheral.getAddress();
                message_list.add(peripheral_name + "#" + peripheral_address);
                message_map.clear();
                message_map.put("bluetooth_list", message_list);
                message_channel.send(message_map);    //  将蓝牙设备信息（蓝牙名称和蓝牙MAC地址）发送给flutter端
            }
        }
        @Override   // 蓝牙连接成功时调用
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
        
        message_channel = new BasicMessageChannel<>(   //  实例化通信通道对象
                binaryMessenger,
                channelName,
                StandardMessageCodec.INSTANCE
        );
        action_map.put("startScanner",      this::scanBleDevice);
        action_map.put("stopScanner",       this::stopScanBleDevice);
        action_map.put("connect",           this::connectBleDevice);
        action_map.put("closeConnect",      this::closeBleDeviceConnect);
        action_map.put("startReader",       this::startReader);
        action_map.put("startReaderEpc",    this::startReaderEpc);
        action_map.put("setAntennaNum",     this::setAntennaNum);
        action_map.put("setAntennaPower",   this::setAntennaPower);
        action_map.put("queryRfidCapacity", this::queryRfidCapacity);
        Log.e("listener_channel_name", channelName);
        
        subscriberHandler();    // 订阅标签TCP事件
        central = new BluetoothCentralManager(    // 实例化蓝牙管理对象
                applicationContext,
                centralManagerCallback,
                new Handler(Looper.getMainLooper()));
        message_channel.setMessageHandler((message, reply) -> {   // 设置通信通道对象监听方法
            arguments = castMap(message, String.class, Object.class);
            if (arguments == null) return;
            String key = getCurrentKey();
            Objects.requireNonNull(action_map.get(key)).accept(key);
//            executeOperation(getCurrentKey());
        });
    }
    
    private void setANTENNA_NUM() {
        /*
        根据当前天线端口号（CURRENT_ANTENNA_NUM）设置读卡时使能的天线端口号（ANTENNA_NUM）
        */
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
        //  根据arguments（Map<String, Object>）中含有的键值设置关键字，以便判断后续需要作出怎样的操作
        String key = null;
        if (arguments.containsKey("startScanner"))            key = "startScanner";
        else if (arguments.containsKey("stopScanner"))        key = "stopScanner";
        else if (arguments.containsKey("bluetoothAddress"))   key = "connect";
        else if (arguments.containsKey("closeConnect"))       key = "closeConnect";
        else if (arguments.containsKey("startReader"))        key = "startReader";
        else if (arguments.containsKey("startReaderEpc"))     key = "startReaderEpc";
        else if (arguments.containsKey("setAntennaNum"))      key = "setAntennaNum";
        else if (arguments.containsKey("setAntennaPower"))    key = "setAntennaPower";
        else if (arguments.containsKey("queryRfidCapacity"))  key = "queryRfidCapacity";
        return key;
    }
    
    @NonNull
    private static String setRfidMessage(  // 将查询到的蓝牙读写能力的信息进行特定处理，以便后续能够区分和解析
            @NonNull MsgBaseGetPower msgBaseGetPower,
            MsgBaseGetCapabilities msgBaseGetCapabilities
    ) {
        StringBuilder current_power = new StringBuilder("current:");
        if (msgBaseGetPower.getRtCode() == 0) {
            Hashtable<Integer, Integer> powers = msgBaseGetPower.getDicPower();
            for (Map.Entry<Integer, Integer> entry : powers.entrySet()) {
                /*
                处理好后的天线端口功率信息会是下面的形式
                [天线端口号1]#[天线端口1功率]@[天线端口号2]#[天线端口2功率]@[天线端口号3]#[天线端口3功率]@........@
                */
                current_power
                        .append(entry.getKey())
                        .append("#")    // 用“#”字符隔开天线端口号和天线端口功率
                        .append(entry.getValue())
                        .append("@");   //  用“@”字符隔开每一个天线
            }
        }
        return  current_power + "&" +       //  然后用“&”隔开当前功率、最小功率、最大功率和天线数量
                "max_power:" + msgBaseGetCapabilities.getMaxPower() + "&" +
                "min_power:" + msgBaseGetCapabilities.getMinPower() + "&" +
                "antenna_count:" + msgBaseGetCapabilities.getAntennaCount() + "&";
    }
    
    private int getCurrentAntennaNum(Long antennaNum) {
        //  根据枚举值判断天线端口号
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
    private void subscriberHandler() {   //  订阅标签TCP事件
        client.onTagEpcLog = (s, logBaseEpcInfo) -> {   // EPC标签上报事件
            if (logBaseEpcInfo.getResult() == 0) {
                Log.e("readerEPC", logBaseEpcInfo.getEpc());
                epcMessages.add(logBaseEpcInfo.getEpc());
            }
        };
        client.onTagEpcOver = (s, logBaseEpcOver) -> {   //  EPC标签上报结束事件
            Log.e("HandlerTagEpcOver", logBaseEpcOver.getRtMsg());
            // send();
            Log.e("epcAppearOver", epcMessages.toString());
            APPEAR_OVER = true;
        };
        
        client.debugLog = new HandlerDebugLog() {   // 错误日志
            public void sendDebugLog(String msg) {
                Log.e("sendDebugLog",msg);
            }
            
            public void receiveDebugLog(String msg) {
                Log.e("receiveDebugLog",msg);
            }
        };
    }
    
    public static <K, V> Map<K, V> castMap(Object obj, Class<K> key, Class<V> value) {
        /*
        对于对象转换为Map类型作出检查
        */
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
        message_list.clear();   // 扫描前先将相关链表清空，以免污染后续数据
        central.scanForPeripherals();   //  扫描附近蓝牙设备
        message_map.clear();    //  发送信息前将集合内容清空，以免信息不正确
        message_map.put("scanMessage", "开始扫描");
        message_channel.send(message_map);
    }
    private void stopScanBleDevice(String key) {  // 停止扫描蓝牙设备
        Object value = arguments.get(key);
        if (value == null) return;
        if (!(boolean) value) return;
        Log.e("扫描设备", "停止扫描");  //  日志，方便后续调试观察运行情况
        message_map.clear();
        message_map.put("scanMessage", "停止扫描");
        message_channel.send(message_map);
        central.stopScan();
    }
    private void connectBleDevice(String key) {
        String bluetooth_address = (String) arguments.get(key);
        BluetoothPeripheral peripheral = central.getPeripheral(bluetooth_address);
        BleDevice device = setBleDevice(peripheral);
        client.openBleDevice(device);   //  连接蓝牙
    }
    @NonNull
    private BleDevice setBleDevice(BluetoothPeripheral peripheral) {
        BleDevice device = new BleDevice(central, peripheral);
        device.setServiceCallback(new BleServiceCallback() {  //  设置蓝牙服务回调函数
            @Override
            public void onServicesDiscovered(BluetoothPeripheral peripheral) {
                List<BluetoothGattService> services = peripheral.getServices();  //  获取所有的服务
                for (BluetoothGattService service : services) {
                    //示例"0000fff0-0000-1000-8000-00805f9b34fb"
                    if (service.getUuid().toString().equals(SERVICE_UUID.toString())) {
                        device.findCharacteristic(service);    // 设置为指定的服务
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
        if (!(boolean) value) return;   // 当值不为真时直接跳出方法，只有为真时才进行后续操作
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
        if (ANTENNA_NUM == 0L) {    //  当使能端口号为0时，则说明在读卡前并未设置使能端口号
            message_map.clear();
            message_map.put("readerOperationMessage", "未配置天线端口，请先配置天线端口");
            message_channel.send(message_map);
            return;  //  当执行完未设置使能端口的相关操作便跳出方法，后续语句不再执行
        }
        //  实例化EPC标签读卡对象
        MsgBaseInventoryEpc msgBaseInventoryEpc = new MsgBaseInventoryEpc();
        //  设置使能端口号
        msgBaseInventoryEpc.setAntennaEnable(ANTENNA_NUM);
        //  设置读卡方式（轮询和单次），此处为单次
        msgBaseInventoryEpc.setInventoryMode(EnumG.InventoryMode_Single);    
        client.sendSynMsg(msgBaseInventoryEpc);   //  发送读卡的同步信息
        boolean operationSuccess = false;
        //  只有当读卡对象返回代码为0时，读卡操作才是成功的
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
        /*  
            读取EPC标签数据，由于标签上报的回调函数中无法进行相应通讯（似乎是阻塞了，详细原因不清楚），
            只能够添加了一个上报结束的标志,只有当上报结束标志为真时才将读取到的EPC标签数据发送给flutter端，
            否则提示flutter端EPC上报尚未结束
        */
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
    private void setAntennaNum(String key) {   //  设置使能端口
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
    private void setAntennaPower(String key) {   //  设置天线端口功率
        MsgBaseSetPower msgBaseSetPower = new MsgBaseSetPower();
        String antenna_message =
                (String) arguments.get(key);
        if (antenna_message == null) return;
        Log.e("power", antenna_message);
        /* 
            flutter端发送过来的天线端口功率格式如何下：
            (去掉了末尾的“&”字符，Android端则不需要进行末尾的空字符串元素进行另外的处理)
            [天线端口1]#[天线端口1功率]&[天线端口2]#[天线端口2功率]&[天线端口3]#[天线端口3功率]......
        */
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
            } else {
                Log.e("设置天线功率", "设置失败");
                message_map.clear();
                message_map.put("AntennaNumMessage", "天线功率设置失败");
                message_channel.send(message_map);
            }
        } else {
            Log.e("设置天线功率", "设置失败");
            message_map.clear();
            message_map.put("AntennaNumMessage", "天线功率设置失败");
            message_channel.send(message_map);
        }
    }
    private void queryRfidCapacity(String key) {   // 查询蓝牙的读写能力信息
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
