package org.petero.droidfish.enginematch;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.petero.droidfish.EngineOptions;
import org.petero.droidfish.book.BookOptions;
import org.petero.droidfish.book.DroidBook;
import org.petero.droidfish.book.IOpeningBook.BookPosInput;
import org.petero.droidfish.book.PgnBook;
import org.petero.droidfish.engine.UCIEngine;
import org.petero.droidfish.engine.UCIEngineBase;
import org.petero.droidfish.gamelogic.ChessParseError;
import org.petero.droidfish.gamelogic.Move;
import org.petero.droidfish.gamelogic.MoveGen;
import org.petero.droidfish.gamelogic.Piece;
import org.petero.droidfish.gamelogic.Position;
import org.petero.droidfish.gamelogic.TextIO;
import org.petero.droidfish.gamelogic.UndoInfo;

public class EngineMatchController {
    private enum State {
        IDLE, STARTING_ENGINE1, STARTING_ENGINE2, PLAYING, STOPPED
    }

    private final EngineMatchListener listener;
    private final EngineOptions engineOptions;
    private EngineMatchConfig config;
    private EngineMatchResult result;

    private UCIEngine engine1;
    public enum AdjudicationResult { NONE, WHITE_WINS, DRAW, BLACK_WINS }

    private UCIEngine engine2;
    private Thread matchThread;
    private volatile boolean stopRequested;
    private volatile AdjudicationResult adjudicationResult = AdjudicationResult.NONE;
    private State state = State.IDLE;

    private Position currentPos;
    private ArrayList<Move> moveHistory;
    private ArrayList<Long> posHashHistory;
    private PgnBook pgnBook;

    private String engine1ReportedName = "";
    private String engine2ReportedName = "";

    private int consecutiveDrawPlies;
    private int consecutiveResignPliesWhite;
    private int consecutiveResignPliesBlack;
    private int lastWhiteScore;
    private int lastBlackScore;

    public EngineMatchController(EngineMatchListener listener, EngineOptions engineOptions) {
        this.listener = listener;
        this.engineOptions = engineOptions;
    }

    public synchronized void startMatch(EngineMatchConfig config) {
        if (state != State.IDLE && state != State.STOPPED)
            return;
        this.config = config;
        this.result = new EngineMatchResult(config.engine1Name, config.engine2Name);
        stopRequested = false;
        state = State.STARTING_ENGINE1;

        matchThread = new Thread(this::runMatch, "EngineMatch");
        matchThread.start();
        listener.onMatchStarted(config);
    }

    public synchronized void stopMatch() {
        stopRequested = true;
        if (matchThread != null)
            matchThread.interrupt();
    }

    public synchronized boolean isRunning() {
        return state == State.PLAYING || state == State.STARTING_ENGINE1 || state == State.STARTING_ENGINE2;
    }

    public synchronized EngineMatchResult getResult() {
        return result;
    }

    public void adjudicateGame(AdjudicationResult adjResult) {
        this.adjudicationResult = adjResult;
    }

    private static final String TAG = "EngineMatch";
    private static final int TIME_MARGIN_MS = 500;

    private void runMatch() {
        Log.d(TAG, "runMatch() started");
        try {
            if (config.bookType == EngineMatchConfig.BookType.PGN &&
                    config.bookFile != null && !config.bookFile.isEmpty()) {
                pgnBook = new PgnBook(new File(config.bookFile));
                pgnBook.setOrder(config.pgnBookOrder);
                pgnBook.setMaxPlies(config.pgnBookMaxPlies);
                pgnBook.parse();
                if (pgnBook.getNumOpenings() == 0) {
                    listener.onMatchError("PGN book contains no valid openings: " + config.bookFile);
                    return;
                }
                listener.onStatusUpdate("Loaded " + pgnBook.getNumOpenings() + " openings from PGN book");
            }

            Log.d(TAG, "Starting engine1: " + config.engine1);
            engine1 = startEngine(config.engine1);
            if (stopRequested) return;
            engine1ReportedName = getEngineName(engine1, config.engine1Name);
            Log.d(TAG, "Engine1 started: " + engine1ReportedName);

            Log.d(TAG, "Starting engine2: " + config.engine2);
            engine2 = startEngine(config.engine2);
            if (stopRequested) return;
            engine2ReportedName = getEngineName(engine2, config.engine2Name);
            Log.d(TAG, "Engine2 started: " + engine2ReportedName);

            synchronized (this) { state = State.PLAYING; }

            for (int gameNum = 1; gameNum <= config.numGames && !stopRequested; gameNum++) {
                boolean engine1IsWhite = config.alternateColors ? (gameNum % 2 == 1) : true;
                playOneGame(gameNum, engine1IsWhite);
            }

            String pgnFile = writePgnFile();
            if (!stopRequested) {
                listener.onMatchFinished(result, pgnFile);
            }
        } catch (Exception e) {
            if (!stopRequested) {
                listener.onMatchError("Match error: " + e.getMessage());
            }
        } finally {
            shutdownEngines();
            synchronized (this) { state = State.STOPPED; }
        }
    }

    private void playOneGame(int gameNum, boolean engine1IsWhite) {
        UCIEngine whiteEngine = engine1IsWhite ? engine1 : engine2;
        UCIEngine blackEngine = engine1IsWhite ? engine2 : engine1;
        String whiteName = engine1IsWhite ? engine1ReportedName : engine2ReportedName;
        String blackName = engine1IsWhite ? engine2ReportedName : engine1ReportedName;

        listener.onGameStarted(gameNum, whiteName, blackName);
        listener.onStatusUpdate(String.format(Locale.US, "Game %d/%d: %s vs %s",
                gameNum, config.numGames, whiteName, blackName));

        adjudicationResult = AdjudicationResult.NONE;
        consecutiveDrawPlies = 0;
        consecutiveResignPliesWhite = 0;
        consecutiveResignPliesBlack = 0;
        lastWhiteScore = 0;
        lastBlackScore = 0;
        initPosition();

        // Emit book moves visually before starting engine play
        ArrayList<Move> bookMoves = new ArrayList<>(moveHistory);
        if (!bookMoves.isEmpty()) {
            // Reset to start position and replay book moves visually
            Position displayPos = safeReadFEN(getStartFen());
            listener.onPositionChanged(new Position(displayPos), null, null,
                    whiteName, blackName, config.timeMs, config.timeMs);
            for (int i = 0; i < bookMoves.size() && !stopRequested; i++) {
                try { Thread.sleep(150); } catch (InterruptedException e) { break; }
                Move bm = bookMoves.get(i);
                String notation = TextIO.moveToString(displayPos, bm, false, false);
                UndoInfo ui = new UndoInfo();
                displayPos.makeMove(bm, ui);
                listener.onPositionChanged(new Position(displayPos), bm, notation,
                        whiteName, blackName, config.timeMs, config.timeMs);
            }
            listener.onStatusUpdate("Book moves complete, engines thinking...");
        }

        if (gameNum > 1) {
            sendNewGame(whiteEngine);
            if (stopRequested) return;
            sendNewGame(blackEngine);
            if (stopRequested) return;
        }

        // Build initial move list from book/opening moves so engines know the full position
        StringBuilder moveList = new StringBuilder();
        for (Move bookMove : moveHistory) {
            if (moveList.length() > 0) moveList.append(" ");
            moveList.append(TextIO.moveToUCIString(bookMove));
        }

        String gameTermination = "";
        long wTimeRemaining = config.timeMs;
        long bTimeRemaining = config.timeMs;

        while (!stopRequested) {
            ArrayList<Move> legalMoves = MoveGen.instance.legalMoves(currentPos);
            if (legalMoves.isEmpty()) {
                if (MoveGen.inCheck(currentPos)) {
                    gameTermination = currentPos.whiteMove ? "Black wins by checkmate" : "White wins by checkmate";
                } else {
                    gameTermination = "Draw by stalemate";
                }
                break;
            }

            if (isDraw()) {
                gameTermination = "Draw";
                break;
            }

            if (adjudicationResult != AdjudicationResult.NONE) {
                switch (adjudicationResult) {
                    case WHITE_WINS: gameTermination = "White wins by adjudication"; break;
                    case BLACK_WINS: gameTermination = "Black wins by adjudication"; break;
                    case DRAW:       gameTermination = "Draw by adjudication"; break;
                    default: break;
                }
                adjudicationResult = AdjudicationResult.NONE;
                break;
            }

            UCIEngine activeEngine = currentPos.whiteMove ? whiteEngine : blackEngine;
            long moveStart = System.currentTimeMillis();
            String bestMove = getEngineMove(activeEngine, moveList.toString(),
                    (int) wTimeRemaining, (int) bTimeRemaining);
            long moveTime = System.currentTimeMillis() - moveStart;

            if (currentPos.whiteMove) {
                wTimeRemaining -= moveTime;
                wTimeRemaining += config.incrementMs;
            } else {
                bTimeRemaining -= moveTime;
                bTimeRemaining += config.incrementMs;
            }

            if (wTimeRemaining + TIME_MARGIN_MS <= 0) {
                gameTermination = "White loses on time";
                break;
            }
            if (bTimeRemaining + TIME_MARGIN_MS <= 0) {
                gameTermination = "Black loses on time";
                break;
            }

            if (bestMove == null || bestMove.isEmpty() || stopRequested) {
                if (adjudicationResult != AdjudicationResult.NONE) {
                    switch (adjudicationResult) {
                        case WHITE_WINS: gameTermination = "White wins by adjudication"; break;
                        case BLACK_WINS: gameTermination = "Black wins by adjudication"; break;
                        case DRAW:       gameTermination = "Draw by adjudication"; break;
                        default: break;
                    }
                    adjudicationResult = AdjudicationResult.NONE;
                } else {
                    gameTermination = "Engine error or match stopped";
                }
                break;
            }

            if (bestMove.startsWith("draw") || bestMove.equals("resign")) {
                if (bestMove.equals("resign")) {
                    gameTermination = (currentPos.whiteMove ? "White" : "Black") + " resigns";
                } else {
                    gameTermination = "Draw by agreement";
                }
                break;
            }

            String moveStr = bestMove.contains(" ") ? bestMove.split(" ")[bestMove.startsWith("draw") ? 2 : 0] : bestMove;
            Move move = TextIO.UCIstringToMove(moveStr);
            if (move == null || !TextIO.isValid(currentPos, move)) {
                gameTermination = "Illegal move from engine: " + moveStr;
                break;
            }

            if (moveList.length() > 0) moveList.append(" ");
            moveList.append(TextIO.moveToUCIString(move));

            String moveNotation = TextIO.moveToString(currentPos, move, false, false);
            boolean wasWhiteMove = currentPos.whiteMove;
            UndoInfo ui = new UndoInfo();
            posHashHistory.add(currentPos.zobristHash());
            currentPos.makeMove(move, ui);
            moveHistory.add(move);

            listener.onPositionChanged(new Position(currentPos), move, moveNotation,
                    whiteName, blackName, wTimeRemaining, bTimeRemaining);

            if (config.maxMoves > 0 && currentPos.fullMoveCounter > config.maxMoves) {
                gameTermination = "Draw by max moves (" + config.maxMoves + ")";
                break;
            }

            if (config.autoAdjudicate) {
                int score = wasWhiteMove ? lastWhiteScore : lastBlackScore;
                int absScore = Math.abs(score);
                boolean isMate = absScore >= 9000;

                if (!isMate && absScore <= config.drawThresholdCp) {
                    consecutiveDrawPlies++;
                } else {
                    consecutiveDrawPlies = 0;
                }
                if (consecutiveDrawPlies >= 10) {
                    gameTermination = "Draw by adjudication (both < " + config.drawThresholdCp + "cp for 10 plies)";
                    break;
                }

                if (!isMate && wasWhiteMove && score <= -config.resignThresholdCp) {
                    consecutiveResignPliesWhite++;
                } else if (wasWhiteMove) {
                    consecutiveResignPliesWhite = 0;
                }
                if (!isMate && !wasWhiteMove && score <= -config.resignThresholdCp) {
                    consecutiveResignPliesBlack++;
                } else if (!wasWhiteMove) {
                    consecutiveResignPliesBlack = 0;
                }
                if (consecutiveResignPliesWhite >= 5 && lastBlackScore >= config.resignThresholdCp) {
                    gameTermination = "Black wins by adjudication (resign threshold)";
                    break;
                }
                if (consecutiveResignPliesBlack >= 5 && lastWhiteScore >= config.resignThresholdCp) {
                    gameTermination = "White wins by adjudication (resign threshold)";
                    break;
                }
            }
        }

        EngineMatchResult.GameResult gameResult;
        if (gameTermination.contains("White wins") || gameTermination.contains("Black resigns") ||
                gameTermination.contains("Black loses on time")) {
            gameResult = EngineMatchResult.GameResult.WHITE_WINS;
        } else if (gameTermination.contains("Black wins") || gameTermination.contains("White resigns") ||
                gameTermination.contains("White loses on time")) {
            gameResult = EngineMatchResult.GameResult.BLACK_WINS;
        } else {
            gameResult = EngineMatchResult.GameResult.DRAW;
        }

        String pgn = buildPgn(whiteName, blackName, gameResult, gameNum);
        EngineMatchResult.GameRecord record = new EngineMatchResult.GameRecord(
                gameNum, whiteName, blackName, gameResult, pgn, gameTermination);
        result.addGame(record);
        listener.onGameFinished(record);
    }

    private static Position safeReadFEN(String fen) {
        try {
            return TextIO.readFEN(fen);
        } catch (ChessParseError e) {
            try {
                return TextIO.readFEN(TextIO.startPosFEN);
            } catch (ChessParseError e2) {
                return new Position();
            }
        }
    }

    private void initPosition() {
        moveHistory = new ArrayList<>();
        posHashHistory = new ArrayList<>();

        if (config.startFen != null && !config.startFen.isEmpty()) {
            currentPos = safeReadFEN(config.startFen);
        } else if (config.bookType == EngineMatchConfig.BookType.PGN && pgnBook != null) {
            currentPos = safeReadFEN(TextIO.startPosFEN);
            playPgnBookMoves();
        } else if (config.bookType == EngineMatchConfig.BookType.POLYGLOT &&
                   config.bookFile != null && !config.bookFile.isEmpty()) {
            currentPos = safeReadFEN(TextIO.startPosFEN);
            playPolyglotBookMoves();
        } else {
            currentPos = safeReadFEN(TextIO.startPosFEN);
        }
    }

    private void playPolyglotBookMoves() {
        DroidBook book = DroidBook.getInstance();
        BookOptions opts = new BookOptions();
        opts.filename = config.bookFile;
        opts.maxLength = config.pgnBookMaxPlies;
        opts.preferMainLines = false;
        opts.tournamentMode = true;
        book.setOptions(opts);

        for (int i = 0; i < config.pgnBookMaxPlies && !stopRequested; i++) {
            BookPosInput posInput = new BookPosInput(currentPos, currentPos, new ArrayList<>());
            Move bookMove = book.getBookMove(posInput);
            if (bookMove == null)
                break;
            UndoInfo ui = new UndoInfo();
            posHashHistory.add(currentPos.zobristHash());
            currentPos.makeMove(bookMove, ui);
            moveHistory.add(bookMove);
        }
    }

    private void playPgnBookMoves() {
        List<Move> opening = pgnBook.nextOpening();
        for (Move move : opening) {
            if (stopRequested)
                break;
            UndoInfo ui = new UndoInfo();
            posHashHistory.add(currentPos.zobristHash());
            currentPos.makeMove(move, ui);
            moveHistory.add(move);
        }
    }

    private boolean isDraw() {
        if (currentPos.halfMoveClock >= 100)
            return true;

        int repCount = 0;
        long currentHash = currentPos.zobristHash();
        for (int i = posHashHistory.size() - 2; i >= 0; i -= 2) {
            if (posHashHistory.get(i) == currentHash) {
                repCount++;
                if (repCount >= 2)
                    return true;
            }
        }

        if (insufficientMaterial())
            return true;

        return false;
    }

    private boolean insufficientMaterial() {
        int minorPieces = 0;
        for (int sq = 0; sq < 64; sq++) {
            int piece = currentPos.getPiece(sq);
            if (piece == Piece.EMPTY) continue;
            if (piece == Piece.WPAWN || piece == Piece.BPAWN ||
                piece == Piece.WROOK || piece == Piece.BROOK ||
                piece == Piece.WQUEEN || piece == Piece.BQUEEN)
                return false;
            if (piece == Piece.WBISHOP || piece == Piece.BBISHOP ||
                piece == Piece.WKNIGHT || piece == Piece.BKNIGHT)
                minorPieces++;
        }
        return minorPieces <= 1;
    }

    private String getEngineMove(UCIEngine engine, String moves, int wTime, int bTime) {
        Log.d(TAG, "getEngineMove: sending isready");
        engine.writeLineToEngine("isready");
        waitForResponse(engine, "readyok", 10000);
        if (stopRequested) return null;

        StringBuilder posCmd = new StringBuilder("position fen ");
        posCmd.append(getStartFen());
        if (!moves.isEmpty()) {
            posCmd.append(" moves ").append(moves);
        }
        Log.d(TAG, "getEngineMove: " + posCmd.toString().substring(0, Math.min(80, posCmd.length())));
        engine.writeLineToEngine(posCmd.toString());

        String goCmd = String.format(Locale.US, "go wtime %d btime %d winc %d binc %d",
                Math.max(1, wTime), Math.max(1, bTime), config.incrementMs, config.incrementMs);
        engine.writeLineToEngine(goCmd);

        long deadline = System.currentTimeMillis() + Math.max(wTime, bTime) + 60000;
        boolean stopSent = false;
        while (!stopRequested && adjudicationResult == AdjudicationResult.NONE) {
            String line = engine.readLineFromEngine(2000);
            if (Thread.currentThread().isInterrupted())
                return null;
            if (line == null) {
                if (System.currentTimeMillis() > deadline)
                    return null;
                continue;
            }
            if (line.isEmpty()) continue;
            Log.d(TAG, "engine output: " + line.substring(0, Math.min(100, line.length())));
            if (line.startsWith("bestmove")) {
                String[] tokens = line.split("\\s+");
                if (tokens.length >= 2)
                    return tokens[1];
                return null;
            }
            if (line.startsWith("info") && line.contains("depth") && line.contains(" pv ")) {
                parseInfoForListener(engine, line);
            } else if (line.startsWith("info") && line.contains("depth") && line.contains("score") && !line.contains("upperbound") && !line.contains("lowerbound")) {
                parseInfoForListener(engine, line);
            }
            if (!stopSent && System.currentTimeMillis() > deadline) {
                engine.writeLineToEngine("stop");
                stopSent = true;
            }
        }
        return null;
    }

    private void parseInfoForListener(UCIEngine engine, String line) {
        try {
            String[] tokens = line.split("\\s+");
            int depth = 0;
            int score = 0;
            ArrayList<String> pvUci = new ArrayList<>();
            for (int i = 1; i < tokens.length; i++) {
                if (tokens[i].equals("depth") && i + 1 < tokens.length)
                    depth = Integer.parseInt(tokens[i + 1]);
                else if (tokens[i].equals("cp") && i + 1 < tokens.length)
                    score = Integer.parseInt(tokens[i + 1]);
                else if (tokens[i].equals("mate") && i + 1 < tokens.length)
                    score = Integer.parseInt(tokens[i + 1]) * 10000;
                else if (tokens[i].equals("pv")) {
                    for (int j = i + 1; j < tokens.length; j++)
                        pvUci.add(tokens[j]);
                    break;
                }
            }
            // Store score for auto-adjudication (score is from side-to-move perspective)
            if (currentPos != null && currentPos.whiteMove) {
                lastWhiteScore = score;
            } else if (currentPos != null) {
                lastBlackScore = score;
            }
            String pv = uciPvToSan(pvUci);
            String engineName = (engine == engine1) ? engine1ReportedName : engine2ReportedName;
            listener.onEngineThinking(engineName, depth, score, pv);
        } catch (NumberFormatException ignored) {
        }
    }

    private String uciPvToSan(ArrayList<String> uciMoves) {
        Position pos = new Position(currentPos);
        StringBuilder sb = new StringBuilder();
        for (String uciMove : uciMoves) {
            Move move = TextIO.UCIstringToMove(uciMove);
            if (move == null || !TextIO.isValid(pos, move))
                break;
            if (sb.length() > 0) sb.append(" ");
            sb.append(TextIO.moveToString(pos, move, false, false));
            UndoInfo ui = new UndoInfo();
            pos.makeMove(move, ui);
        }
        return sb.toString();
    }

    private String getStartFen() {
        if (config.startFen != null && !config.startFen.isEmpty())
            return config.startFen;
        return TextIO.startPosFEN;
    }

    private String buildPgn(String white, String black,
                            EngineMatchResult.GameResult result, int round) {
        StringBuilder pgn = new StringBuilder();
        pgn.append("[Event \"Engine Match\"]\n");
        pgn.append("[Site \"DroidFish\"]\n");
        pgn.append(String.format("[Round \"%d\"]\n", round));
        pgn.append(String.format("[White \"%s\"]\n", white));
        pgn.append(String.format("[Black \"%s\"]\n", black));
        String resultStr;
        switch (result) {
            case WHITE_WINS: resultStr = "1-0"; break;
            case BLACK_WINS: resultStr = "0-1"; break;
            default:         resultStr = "1/2-1/2"; break;
        }
        pgn.append(String.format("[Result \"%s\"]\n", resultStr));
        pgn.append("\n");

        Position pos = safeReadFEN(getStartFen());
        for (int i = 0; i < moveHistory.size(); i++) {
            if (pos.whiteMove) {
                pgn.append(String.format("%d. ", pos.fullMoveCounter));
            } else if (i == 0) {
                pgn.append(String.format("%d... ", pos.fullMoveCounter));
            }
            pgn.append(TextIO.moveToString(pos, moveHistory.get(i), false, false));
            pgn.append(" ");
            UndoInfo ui = new UndoInfo();
            pos.makeMove(moveHistory.get(i), ui);
        }
        pgn.append(resultStr);
        return pgn.toString();
    }

    private UCIEngine startEngine(String engineId) {
        Log.d(TAG, "startEngine: creating engine for " + engineId);
        UCIEngine engine = UCIEngineBase.getEngine(engineId, engineOptions, errMsg -> {
            if (errMsg != null) {
                Log.e(TAG, "Engine error callback: " + errMsg);
                listener.onMatchError("Engine error: " + errMsg);
            }
        });
        Log.d(TAG, "startEngine: calling initialize()");
        engine.initialize();
        Log.d(TAG, "startEngine: calling clearOptions()");
        engine.clearOptions();
        Log.d(TAG, "startEngine: sending 'uci'");
        engine.writeLineToEngine("uci");
        waitForUciOk(engine);
        Log.d(TAG, "startEngine: got uciok, calling initOptions+applyIniFile");
        engine.initOptions(engineOptions);
        engine.applyIniFile();
        engine.writeLineToEngine("setoption name Ponder value false");
        Log.d(TAG, "startEngine: sending ucinewgame+isready");
        engine.writeLineToEngine("ucinewgame");
        engine.writeLineToEngine("isready");
        waitForResponse(engine, "readyok", 10000);
        Log.d(TAG, "startEngine: engine ready");
        return engine;
    }

    private void waitForUciOk(UCIEngine engine) {
        long deadline = System.currentTimeMillis() + 30000;
        while (!stopRequested && System.currentTimeMillis() < deadline) {
            String line = engine.readLineFromEngine(1000);
            if (line == null) continue;
            if (line.equals("uciok")) return;
            if (line.startsWith("id name ")) {
                // Capture reported engine name
            }
            if (line.startsWith("option")) {
                String[] tokens = line.split("\\s+");
                engine.registerOption(tokens);
            }
        }
    }

    private String getEngineName(UCIEngine engine, String fallback) {
        return fallback;
    }

    private void sendNewGame(UCIEngine engine) {
        Log.d(TAG, "sendNewGame: sending ucinewgame+isready");
        engine.writeLineToEngine("ucinewgame");
        engine.writeLineToEngine("isready");
        waitForResponse(engine, "readyok", 30000);
        Log.d(TAG, "sendNewGame: got readyok");
    }

    private void waitForResponse(UCIEngine engine, String expected, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!stopRequested && System.currentTimeMillis() < deadline) {
            String line = engine.readLineFromEngine(2000);
            if (line == null) continue;
            Log.d(TAG, "waitForResponse('" + expected + "'): got: " + line);
            if (line.startsWith(expected))
                return;
        }
        Log.w(TAG, "waitForResponse('" + expected + "'): TIMED OUT after " + timeoutMs + "ms");
    }

    private String writePgnFile() {
        if (result == null || result.getGames().isEmpty())
            return null;
        try {
            File pgnDir = new File(engineOptions.workDir).getParentFile();
            File pgnBaseDir = new File(pgnDir, "pgn");
            if (!pgnBaseDir.exists()) pgnBaseDir.mkdirs();

            String name1 = trimEngineName(engine1ReportedName);
            String name2 = trimEngineName(engine2ReportedName);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = String.format("enginematch_%s_%s_%s.pgn", name1, name2, timestamp);
            File pgnFile = new File(pgnBaseDir, fileName);

            FileWriter writer = new FileWriter(pgnFile);
            for (EngineMatchResult.GameRecord game : result.getGames()) {
                writer.write(game.pgn);
                writer.write("\n\n");
            }
            writer.close();
            Log.d(TAG, "PGN written to: " + pgnFile.getAbsolutePath());
            return pgnFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write PGN file: " + e.getMessage());
            return null;
        }
    }

    private String trimEngineName(String name) {
        String trimmed = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (trimmed.length() > 15) trimmed = trimmed.substring(0, 15);
        return trimmed;
    }

    private void shutdownEngines() {
        if (engine1 != null) {
            try { engine1.writeLineToEngine("quit"); } catch (Exception ignored) {}
            engine1.shutDown();
            engine1 = null;
        }
        if (engine2 != null) {
            try { engine2.writeLineToEngine("quit"); } catch (Exception ignored) {}
            engine2.shutDown();
            engine2 = null;
        }
    }
}
