package org.petero.droidfish.enginematch;

import org.petero.droidfish.gamelogic.Move;
import org.petero.droidfish.gamelogic.Position;

public interface EngineMatchListener {
    void onMatchStarted(EngineMatchConfig config);
    void onGameStarted(int gameNumber, String whiteEngine, String blackEngine);
    void onPositionChanged(Position pos, Move lastMove, String lastMoveNotation,
                           String whiteEngine, String blackEngine,
                           long wTimeMs, long bTimeMs);
    void onEngineThinking(String engineName, int depth, int score, String pv);
    void onGameFinished(EngineMatchResult.GameRecord record);
    void onMatchFinished(EngineMatchResult result, String pgnFilePath);
    void onMatchError(String error);
    void onStatusUpdate(String status);
}
