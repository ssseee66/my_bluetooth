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
    private BasicMessageChannel<Object> flutter_channel;
    private Context applicationContext;
    private Map<String, MyListener> listeners = new HashMap<>();
    
    
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        Log.e("onAttachedToEngine", "onAttachedToEngine");
        applicationContext = flutterPluginBinding.getApplicationContext();
        
        flutter_channel = new BasicMessageChannel<>(
                flutterPluginBinding.getBinaryMessenger(),
                FLUTTER_TO_ANDROID_CHANNEL,
                StandardMessageCodec.INSTANCE
        );
        
        flutter_channel.setMessageHandler((message, reply) -> {
            Map<String, Object> channelMessage = (Map<String, Object>)message;
            if (channelMessage != null) {
                if (channelMessage.containsKey("channelName")) {
                    String channel_name = (String) channelMessage.get("channelName");
                    Log.e("channelName", channel_name);
                    MyListener listener = new MyListener(
                                            channel_name,
                                            applicationContext,
                                            flutterPluginBinding.getBinaryMessenger());
                    // if (!listeners.containsKey(channel_name)) {
                    //     MyListener listener =
                    //             new MyListener(
                    //                     channel_name,
                    //                     applicationContext,
                    //                     flutterPluginBinding.getBinaryMessenger()
                    //             );
                    //     listeners.put(channel_name, listener);
                    //     Log.e("listeners", listeners.toString());
                    // }
                }
            }
        });
    }
    
    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    
    }
}
