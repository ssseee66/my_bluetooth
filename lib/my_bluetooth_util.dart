import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class MyBluetoothUtil {
  MyBluetoothUtil._();

  factory MyBluetoothUtil() => _instance;
  static final MyBluetoothUtil _instance = MyBluetoothUtil._();

  String messageChannelName = "";
  BasicMessageChannel flutterChannel = const BasicMessageChannel("flutter_and_android", StandardMessageCodec());
  BasicMessageChannel messageChannel = const BasicMessageChannel("null", StandardMessageCodec());

  void sendMessageToAndroid(String methodName, dynamic arg) async {
    messageChannel.send({methodName: arg});
  }

  void sendChannelName(String methodName, dynamic channelName) async {
    flutterChannel.send({methodName: channelName});
  }

  void setMessageChannel(String channel_name, Future<dynamic> Function(dynamic message) handler) {
    if (channel_name != null) {
      messageChannel = BasicMessageChannel(channel_name, StandardMessageCodec());
      messageChannel.setMessageHandler(handler);
    }
  }

}