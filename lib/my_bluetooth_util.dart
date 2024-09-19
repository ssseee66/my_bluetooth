import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class MyBluetoothUtil {
  MyBluetoothUtil._();

  factory MyBluetoothUtil() => _instance;
  static final MyBluetoothUtil _instance = MyBluetoothUtil._();

  BasicMessageChannel flutterChannel = const BasicMessageChannel("flutter_and_android", StandardMessageCodec());

  void sendMessageToAndroid(String methodName, dynamic arg) async {
    flutterChannel.send({methodName: arg});
  }

}