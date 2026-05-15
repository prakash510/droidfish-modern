package org.petero.droidfish.enginematch;

import org.petero.droidfish.book.PgnBook;

public class EngineMatchConfig {
    public enum BookType { NONE, POLYGLOT, PGN }

    public String engine1;
    public String engine2;
    public String engine1Name;
    public String engine2Name;
    public int timeMs;
    public int incrementMs;
    public int numGames;
    public String startFen;
    public String bookFile;
    public boolean useBook;
    public boolean alternateColors;
    public boolean keepScreenOn;
    public BookType bookType;
    public PgnBook.Order pgnBookOrder;
    public int pgnBookMaxPlies;
    public int maxMoves;
    public boolean autoAdjudicate;
    public int drawThresholdCp;
    public int resignThresholdCp;

    public EngineMatchConfig() {
        engine1 = "stockfish";
        engine2 = "stockfish";
        engine1Name = "Stockfish";
        engine2Name = "Stockfish";
        timeMs = 60000;
        incrementMs = 1000;
        numGames = 2;
        startFen = "";
        bookFile = "";
        useBook = false;
        alternateColors = true;
        keepScreenOn = false;
        bookType = BookType.NONE;
        pgnBookOrder = PgnBook.Order.SEQUENTIAL;
        pgnBookMaxPlies = 20;
        maxMoves = 200;
        autoAdjudicate = false;
        drawThresholdCp = 10;
        resignThresholdCp = 500;
    }
}
