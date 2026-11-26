import 'package:flutter/material.dart';
import 'package:flutter_chess_board/flutter_chess_board.dart';
import 'screen_capture_service.dart';
import 'chess_vision_service.dart';
import 'stockfish_service.dart';

void main() => runApp(const ChessAssistantApp());

class ChessAssistantApp extends StatelessWidget {
  const ChessAssistantApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Chess Screen Assistant',
      theme: ThemeData.dark(),
      home: const AssistantHomePage(),
    );
  }
}

class AssistantHomePage extends StatefulWidget {
  const AssistantHomePage({super.key});
  @override
  State<AssistantHomePage> createState() => _AssistantHomePageState();
}

class _AssistantHomePageState extends State<AssistantHomePage> {
  final ChessBoardController _boardController = ChessBoardController();
  String _currentFen = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';
  String? _bestMove;
  String _status = 'Готов к анализу';
  bool _isAnalyzing = false;

  Future<void> _requestPermission() async {
    final granted = await ScreenCaptureService.requestPermission();
    setState(() => _status = granted ? 'Разрешение получено' : 'Разрешение отклонено');
  }

  Future<void> _runPipeline() async {
    setState(() {
      _isAnalyzing = true;
      _status = 'Захват экрана...';
    });
    try {
      final imageBytes = await ScreenCaptureService.capture();
      if (imageBytes == null) {
        setState(() => _status = 'Ошибка захвата экрана');
        return;
      }

      setState(() => _status = 'Распознавание доски...');
      final fen = await ChessVisionService.recognizeFen(imageBytes);
      if (fen == null) {
        setState(() => _status = 'Доска не распознана');
        return;
      }
      setState(() {
        _currentFen = fen;
        _status = 'Анализ Stockfish...';
      });

      final bestMove = await StockfishService.getBestMove(fen);
      setState(() {
        _bestMove = bestMove;
        _status = bestMove != null ? 'Лучший ход: $bestMove' : 'Движок не ответил';
      });
    } catch (e) {
      setState(() => _status = 'Ошибка: $e');
    } finally {
      setState(() => _isAnalyzing = false);
    }
  }

  List<BoardArrow> get _arrows {
    if (_bestMove == null || _bestMove!.length < 4) return [];
    return [
      BoardArrow(
        from: _bestMove!.substring(0, 2),
        to: _bestMove!.substring(2, 4),
        color: Colors.green,
      )
    ];
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Шахматный ассистент')),
      body: Column(
        children: [
          Expanded(
            child: ChessBoard(
              controller: _boardController,
              boardType: BoardType.standard,
              fen: _currentFen,
              arrows: _arrows,
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(12.0),
            child: Column(
              children: [
                Text(_status, style: const TextStyle(fontSize: 14)),
                const SizedBox(height: 8),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    ElevatedButton.icon(
                      onPressed: _isAnalyzing ? null : _requestPermission,
                      icon: const Icon(Icons.security),
                      label: const Text('Разрешение'),
                    ),
                    ElevatedButton.icon(
                      onPressed: _isAnalyzing ? null : _runPipeline,
                      icon: _isAnalyzing
                          ? const SizedBox(
                              width: 16, height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2))
                          : const Icon(Icons.analytics),
                      label: Text(_isAnalyzing ? 'Анализ...' : 'Анализировать'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
