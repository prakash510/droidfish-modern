package org.petero.droidfish.book;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.petero.droidfish.gamelogic.Move;
import org.petero.droidfish.gamelogic.Position;
import org.petero.droidfish.gamelogic.TextIO;
import org.petero.droidfish.gamelogic.ChessParseError;
import org.petero.droidfish.gamelogic.UndoInfo;

/**
 * Opening book backed by a PGN file. Each game in the PGN is treated as one
 * opening line. Games are selected sequentially or randomly, and moves are
 * played up to a configurable ply depth before handing control to the engines.
 */
public class PgnBook {

    public enum Order { SEQUENTIAL, RANDOM }

    private final File pgnFile;
    private final List<List<Move>> openings = new ArrayList<>();
    private int nextIndex = 0;
    private Order order = Order.SEQUENTIAL;
    private int maxPlies = 30;

    public PgnBook(File pgnFile) {
        this.pgnFile = pgnFile;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public void setMaxPlies(int maxPlies) {
        this.maxPlies = maxPlies;
    }

    public int getNumOpenings() {
        return openings.size();
    }

    /**
     * Parse the PGN file and extract opening move sequences.
     * Each game becomes one opening, truncated to maxPlies half-moves.
     */
    public void parse() throws IOException {
        openings.clear();
        nextIndex = 0;

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(pgnFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
        }

        List<String> games = splitGames(content.toString());
        for (String game : games) {
            List<Move> moves = parseGame(game);
            if (!moves.isEmpty()) {
                openings.add(moves);
            }
        }

        if (order == Order.RANDOM) {
            Collections.shuffle(openings, new Random());
        }
    }

    /**
     * Get the next opening as a list of moves. Wraps around when all
     * openings have been used.
     */
    public List<Move> nextOpening() {
        if (openings.isEmpty())
            return new ArrayList<>();
        if (nextIndex >= openings.size())
            nextIndex = 0;
        return new ArrayList<>(openings.get(nextIndex++));
    }

    private List<String> splitGames(String content) {
        List<String> games = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inGame = false;

        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.startsWith("[Event ") && inGame && current.length() > 0) {
                games.add(current.toString());
                current = new StringBuilder();
            }
            if (line.startsWith("["))
                inGame = true;
            current.append(line).append('\n');
        }
        if (current.length() > 0 && inGame) {
            games.add(current.toString());
        }
        return games;
    }

    private List<Move> parseGame(String gameText) {
        List<Move> moves = new ArrayList<>();

        String fen = extractFen(gameText);
        Position pos;
        try {
            pos = TextIO.readFEN(fen != null ? fen : TextIO.startPosFEN);
        } catch (ChessParseError e) {
            return moves;
        }

        String moveText = extractMoveText(gameText);
        String[] tokens = tokenize(moveText);

        for (String token : tokens) {
            if (moves.size() >= maxPlies)
                break;
            if (token.isEmpty())
                continue;
            if (token.equals("1-0") || token.equals("0-1") || token.equals("1/2-1/2") || token.equals("*"))
                break;
            if (token.endsWith("."))
                continue;
            if (token.startsWith("$"))
                continue;

            // Strip move number prefix (e.g., "1." or "1...")
            String moveStr = token.replaceAll("^\\d+\\.+", "");
            if (moveStr.isEmpty())
                continue;

            Move move = TextIO.stringToMove(pos, moveStr);
            if (move == null)
                break;

            moves.add(move);
            UndoInfo ui = new UndoInfo();
            pos.makeMove(move, ui);
        }
        return moves;
    }

    private String extractFen(String gameText) {
        String[] lines = gameText.split("\n");
        for (String line : lines) {
            if (line.startsWith("[FEN \"") && line.endsWith("\"]")) {
                return line.substring(6, line.length() - 2);
            }
        }
        return null;
    }

    private String extractMoveText(String gameText) {
        StringBuilder sb = new StringBuilder();
        String[] lines = gameText.split("\n");
        boolean pastHeaders = false;
        for (String line : lines) {
            if (line.startsWith("[")) {
                pastHeaders = false;
                continue;
            }
            if (!line.startsWith("[")) {
                pastHeaders = true;
            }
            if (pastHeaders) {
                sb.append(line).append(' ');
            }
        }
        return sb.toString();
    }

    private String[] tokenize(String moveText) {
        // Remove comments
        String cleaned = moveText.replaceAll("\\{[^}]*}", "");
        // Remove variations
        cleaned = removeVariations(cleaned);
        // Remove RAV and NAG
        cleaned = cleaned.replaceAll("\\$\\d+", "");
        // Split on whitespace
        return cleaned.trim().split("\\s+");
    }

    private String removeVariations(String text) {
        StringBuilder result = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0) {
                result.append(c);
            }
        }
        return result.toString();
    }
}
