import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:my_bluetooth/my_bluetooth_util.dart';

mixin RfidReaderMixin<T extends StatefulWidget> on State<T> {
  late StreamSubscription streamSubscription;
  final MyBluetoothUtil util = MyBluetoothUtil();

  @override
  void initState() {
    super.initState();
    util.flutterChannel.setMessageHandler(listenerAndroidHandle);
  }

  Future<void> listenerAndroidHandle(dynamic message);

}