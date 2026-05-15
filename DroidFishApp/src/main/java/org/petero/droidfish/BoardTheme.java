package org.petero.droidfish;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import java.io.IOException;
import java.io.InputStream;

/**
 * Manages board texture rendering. Supports both flat color themes and
 * full-board texture images (like Lichess wood/marble/etc).
 */
public class BoardTheme {
    private static BoardTheme inst = null;

    private Bitmap boardTexture;
    private String cachedTextureName = "";
    private int cachedBoardSize = -1;
    private Bitmap scaledTexture;

    public static BoardTheme instance() {
        if (inst == null)
            inst = new BoardTheme();
        return inst;
    }

    private BoardTheme() {}

    /** Available board texture names (matching assets/boards/ filenames without extension). */
    public static final String[] TEXTURE_NAMES = {
        "", "brown", "blue", "blue2", "blue3", "green", "green-plastic",
        "wood", "wood2", "wood3", "wood4", "maple", "maple2",
        "marble", "dark-wood", "walnut", "rosewood", "leather", "olive",
        "canvas", "blue-marble", "grey", "metal", "purple", "purple-diag",
        "horsey", "ic"
    };

    public static final String[] TEXTURE_DISPLAY_NAMES = {
        "Flat Color", "Brown", "Blue", "Blue 2", "Blue 3", "Green", "Green Plastic",
        "Wood", "Wood 2", "Wood 3", "Wood 4", "Maple", "Maple 2",
        "Marble", "Dark Wood", "Walnut", "Rosewood", "Leather", "Olive",
        "Canvas", "Blue Marble", "Grey", "Metal", "Purple", "Purple Diag",
        "Horsey", "IC"
    };

    public void readSettings(SharedPreferences settings) {
        String textureName = settings.getString("boardTexture", "");
        if (!textureName.equals(cachedTextureName)) {
            cachedTextureName = textureName;
            boardTexture = null;
            scaledTexture = null;
            cachedBoardSize = -1;
            if (!textureName.isEmpty()) {
                loadTexture(textureName);
            }
        }
    }

    private void loadTexture(String name) {
        Context ctx = DroidFishApp.getContext();
        if (ctx == null) return;
        try {
            String ext = "jpg";
            InputStream is;
            try {
                is = ctx.getAssets().open("boards/" + name + ".jpg");
            } catch (IOException e) {
                is = ctx.getAssets().open("boards/" + name + ".png");
                ext = "png";
            }
            boardTexture = BitmapFactory.decodeStream(is);
            is.close();
        } catch (IOException e) {
            boardTexture = null;
        }
    }

    /** Returns true if a texture is active (not flat color mode). */
    public boolean hasTexture() {
        return boardTexture != null;
    }

    /**
     * Draw the board texture onto the canvas for the given board area.
     * @param canvas The canvas to draw on
     * @param x0 Left edge of board
     * @param y0 Top edge of board
     * @param boardSize Total board size in pixels (8 * sqSize)
     */
    public void drawBoard(Canvas canvas, int x0, int y0, int boardSize) {
        if (boardTexture == null) return;
        if (boardSize != cachedBoardSize || scaledTexture == null) {
            cachedBoardSize = boardSize;
            scaledTexture = Bitmap.createScaledBitmap(boardTexture, boardSize, boardSize, true);
        }
        canvas.drawBitmap(scaledTexture, x0, y0, null);
    }

    /**
     * Draw a single square from the board texture.
     * @param canvas Canvas to draw on
     * @param sqX Square x coordinate (0-7, a=0)
     * @param sqY Square y coordinate (0-7, 1=0)
     * @param xCrd Pixel x of square top-left
     * @param yCrd Pixel y of square top-left
     * @param sqSize Square size in pixels
     * @param flipped Whether board is flipped
     * @param paint Paint to use for drawing (may have color filter for brightness)
     */
    public void drawSquare(Canvas canvas, int sqX, int sqY, int xCrd, int yCrd,
                           int sqSize, boolean flipped, Paint paint) {
        if (boardTexture == null) return;
        int texSize = boardTexture.getWidth();
        int texSqSize = texSize / 8;

        int texX = flipped ? (7 - sqX) : sqX;
        int texY = flipped ? sqY : (7 - sqY);

        Rect src = new Rect(texX * texSqSize, texY * texSqSize,
                           (texX + 1) * texSqSize, (texY + 1) * texSqSize);
        Rect dst = new Rect(xCrd, yCrd, xCrd + sqSize, yCrd + sqSize);
        canvas.drawBitmap(boardTexture, src, dst, paint);
    }
}
