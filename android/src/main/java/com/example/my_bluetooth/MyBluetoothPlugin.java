package com.example.my_bluetooth;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BasicMessageChannel;
import io.flutter.plugin.common.StandardMessageCodec;

/** RfidReaderPlugin */
public class MyBluetoothPlugin implements FlutterPlugin {
    private static final String FLUTTER_TO_ANDROID_CHANNEL = "flutter_and_android";
    private Context applicationContext;
    
    
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        Log.e("onAttachedToEngine", "onAttachedToEngine");
        applicationContext = flutterPluginBinding.getApplicationContext();
        
        BasicMessageChannel<Object> flutter_channel = new BasicMessageChannel<>(
                flutterPluginBinding.getBinaryMessenger(),
                FLUTTER_TO_ANDROID_CHANNEL,
                StandardMessageCodec.INSTANCE
        );
        
        flutter_channel.setMessageHandler((message, reply) -> {
            Map<String, Object> channelMessage = castMap(message, String.class, Object.class);
            if (channelMessage == null) return;
            if (channelMessage.containsKey("channelName")) {
                String channel_name = (String) channelMessage.get("channelName");
                if (channel_name == null) return;
                Log.e("channelName", channel_name);
                new MyListener(
                        channel_name,
                        applicationContext,
                        flutterPluginBinding.getBinaryMessenger());
            }
        });
    }
    
    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    
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
}
