import 'dart:typed_data';
import 'package:flutter/services.dart';

class ScreenCaptureService {
  static const _channel = MethodChannel('chess_assistant/screen_capture');

  static Future<bool> requestPermission() async {
    try {
      final result = await _channel.invokeMethod<bool>('requestPermission');
      return result ?? false;
    } on PlatformException {
      return false;
    }
  }

  static Future<Uint8List?> capture() async {
    try {
      final bytes = await _channel.invokeMethod<Uint8List>('capture');
      return bytes;
    } on PlatformException {
      return null;
    }
  }
}
