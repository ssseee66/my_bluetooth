import 'package:flutter_test/flutter_test.dart';
import 'package:my_bluetooth/my_bluetooth.dart';
import 'package:my_bluetooth/my_bluetooth_platform_interface.dart';
import 'package:my_bluetooth/my_bluetooth_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockMyBluetoothPlatform
    with MockPlatformInterfaceMixin
    implements MyBluetoothPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final MyBluetoothPlatform initialPlatform = MyBluetoothPlatform.instance;

  test('$MethodChannelMyBluetooth is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelMyBluetooth>());
  });

  test('getPlatformVersion', () async {
    MyBluetooth myBluetoothPlugin = MyBluetooth();
    MockMyBluetoothPlatform fakePlatform = MockMyBluetoothPlatform();
    MyBluetoothPlatform.instance = fakePlatform;

    expect(await myBluetoothPlugin.getPlatformVersion(), '42');
  });
}
