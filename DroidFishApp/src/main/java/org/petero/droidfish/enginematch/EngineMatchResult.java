package org.petero.droidfish.enginematch;

import java.util.ArrayList;
import java.util.Locale;

public class EngineMatchResult {
    public enum GameResult {
        WHITE_WINS, BLACK_WINS, DRAW
    }

    public static class GameRecord {
        public final int gameNumber;
        public final String whiteEngine;
        public final String blackEngine;
        public final GameResult result;
        public final String pgn;
        public final String termination;

        public GameRecord(int gameNumber, String whiteEngine, String blackEngine,
                          GameResult result, String pgn, String termination) {
            this.gameNumber = gameNumber;
            this.whiteEngine = whiteEngine;
            this.blackEngine = blackEngine;
            this.result = result;
            this.pgn = pgn;
            this.termination = termination;
        }

        public String resultString() {
            switch (result) {
                case WHITE_WINS: return "1-0";
                case BLACK_WINS: return "0-1";
                case DRAW:       return "1/2-1/2";
                default:         return "*";
            }
        }
    }

    private final ArrayList<GameRecord> games = new ArrayList<>();
    private final String engine1Name;
    private final String engine2Name;
    private int engine1Wins = 0;
    private int engine2Wins = 0;
    private int draws = 0;

    public EngineMatchResult(String engine1Name, String engine2Name) {
        this.engine1Name = engine1Name;
        this.engine2Name = engine2Name;
    }

    public void addGame(GameRecord record) {
        games.add(record);
        if (record.whiteEngine.equals(engine1Name)) {
            if (record.result == GameResult.WHITE_WINS) engine1Wins++;
            else if (record.result == GameResult.BLACK_WINS) engine2Wins++;
            else draws++;
        } else {
            if (record.result == GameResult.WHITE_WINS) engine2Wins++;
            else if (record.result == GameResult.BLACK_WINS) engine1Wins++;
            else draws++;
        }
    }

    public int getEngine1Wins() { return engine1Wins; }
    public int getEngine2Wins() { return engine2Wins; }
    public int getDraws() { return draws; }
    public int getTotalGames() { return games.size(); }
    public ArrayList<GameRecord> getGames() { return games; }

    public double getEngine1Score() {
        return engine1Wins + 0.5 * draws;
    }

    public double getEngine2Score() {
        return engine2Wins + 0.5 * draws;
    }

    public String getSummary() {
        return String.format(Locale.US, "%s vs %s: +%d -%d =%d (%.1f - %.1f)",
                engine1Name, engine2Name,
                engine1Wins, engine2Wins, draws,
                getEngine1Score(), getEngine2Score());
    }

    public String getPentanomial() {
        int[] penta = new int[5]; // [LL, LD/DL, DD/WL/LW, WD/DW, WW] from engine1's perspective
        for (int i = 0; i + 1 < games.size(); i += 2) {
            GameRecord g1 = games.get(i);
            GameRecord g2 = games.get(i + 1);
            int pairPoints = pairPoints(g1) + pairPoints(g2);
            switch (pairPoints) {
                case 0: penta[0]++; break; // LL
                case 1: penta[1]++; break; // LD or DL
                case 2: penta[2]++; break; // DD, WL, or LW
                case 3: penta[3]++; break; // WD or DW
                case 4: penta[4]++; break; // WW
            }
        }
        return String.format(Locale.US, "[%d, %d, %d, %d, %d]",
                penta[0], penta[1], penta[2], penta[3], penta[4]);
    }

    private int pairPoints(GameRecord game) {
        if (game.whiteEngine.equals(engine1Name)) {
            if (game.result == GameResult.WHITE_WINS) return 2;
            else if (game.result == GameResult.DRAW) return 1;
            else return 0;
        } else {
            if (game.result == GameResult.BLACK_WINS) return 2;
            else if (game.result == GameResult.DRAW) return 1;
            else return 0;
        }
    }

    public String getEloEstimate() {
        int n = getTotalGames();
        if (n == 0) return "";
        double score = getEngine1Score() / n;
        if (score <= 0.0 || score >= 1.0) {
            String result = score >= 1.0 ? "Elo: +inf" : "Elo: -inf";
            return result + String.format(Locale.US, "\nLOS: %.1f%%", score >= 1.0 ? 100.0 : 0.0);
        }
        double eloDiff = -400.0 * Math.log10(1.0 / score - 1.0);
        double los = 0.5 + 0.5 * erf((getEngine1Score() - getEngine2Score()) /
                Math.sqrt(2.0 * (engine1Wins + engine2Wins + draws)));
        double errorMargin = 0.0;
        if (n >= 2) {
            double variance = (score * (1.0 - score)) / n;
            double se = Math.sqrt(variance);
            double eloDenom = score * (1.0 - score);
            errorMargin = 1.96 * se * 400.0 / (Math.log(10) * eloDenom);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "Elo: %+.1f ± %.1f", eloDiff, errorMargin));
        sb.append(String.format(Locale.US, "\nLOS: %.1f%%", los * 100.0));
        return sb.toString();
    }

    private static double erf(double x) {
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        double poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 +
                t * (-1.453152027 + t * 1.061405429))));
        double result = 1.0 - poly * Math.exp(-x * x);
        return x >= 0 ? result : -result;
    }
}
