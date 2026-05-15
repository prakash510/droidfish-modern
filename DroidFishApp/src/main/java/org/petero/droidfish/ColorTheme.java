/*
    DroidFish - An Android chess program.
    Copyright (C) 2011  Peter Österlund, peterosterlund2@gmail.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.petero.droidfish;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;

public class ColorTheme {
    private static ColorTheme inst = null;

    /** Get singleton instance. */
    public static ColorTheme instance() {
        if (inst == null)
            inst = new ColorTheme();
        return inst;
    }

    public final static int DARK_SQUARE = 0;
    public final static int BRIGHT_SQUARE = 1;
    public final static int SELECTED_SQUARE = 2;
    public final static int DARK_PIECE = 3;
    public final static int BRIGHT_PIECE = 4;
    public final static int CURRENT_MOVE = 5;
    public final static int ARROW_0 = 6;
    public final static int ARROW_1 = 7;
    public final static int ARROW_2 = 8;
    public final static int ARROW_3 = 9;
    public final static int ARROW_4 = 10;
    public final static int ARROW_5 = 11;
    public final static int ARROW_6 = 12;
    public final static int ARROW_7 = 13;
    public final static int MAX_ARROWS = 8;
    public final static int SQUARE_LABEL = 14;
    public final static int DECORATION = 15;
    public final static int PGN_COMMENT = 16;
    public final static int FONT_FOREGROUND = 17;
    public final static int GENERAL_BACKGROUND = 18;
    private final static int numColors = 19;

    private int[] colorTable = new int[numColors];

    private static final String[] prefNames = {
        "darkSquare", "brightSquare", "selectedSquare", "darkPiece", "brightPiece", "currentMove",
        "arrow0", "arrow1", "arrow2", "arrow3", "arrow4", "arrow5", "arrow6", "arrow7",
        "squareLabel", "decoration", "pgnComment", "fontForeground", "generalBackground"
    };
    private static final String prefPrefix = "color_";

    private final static int defaultTheme = 8;
    final static int[] themeNames = {
        R.string.colortheme_original,
        R.string.colortheme_xboard,
        R.string.colortheme_blue,
        R.string.colortheme_grey,
        R.string.colortheme_scid_default,
        R.string.colortheme_scid_brown,
        R.string.colortheme_scid_green,
        R.string.colortheme_lichess_brown,
        R.string.colortheme_lichess_blue,
        R.string.colortheme_lichess_green,
        R.string.colortheme_midnight,
        R.string.colortheme_lichess_purple,
        R.string.colortheme_lichess_pink,
        R.string.colortheme_lichess_ic,
        R.string.colortheme_lichess_wood,
        R.string.colortheme_lichess_canvas,
        R.string.colortheme_lichess_marble
    };
    private final static String[][] themeColors = {
    { // Original
        "#FF808080", "#FFBEBE5A", "#FFFF0000", "#FF000000", "#FFFFFFFF", "#FF888888",
        "#A01F1FFF", "#A0FF1F1F", "#501F1FFF", "#50FF1F1F", "#371F1FFF", "#3CFF1F1F", "#1E1F1FFF", "#28FF1F1F",
        "#FFFF0000", "#FF9F9F66", "#FFC0C000", "#FFF7FBC6", "#FF292C10"
    },
    { // XBoard
        "#FF77A26D", "#FFC8C365", "#FFFFFF00", "#FF202020", "#FFFFFFCC", "#FF6B9262",
        "#A01F1FFF", "#A0FF1F1F", "#501F1FFF", "#50FF1F1F", "#371F1FFF", "#3CFF1F1F", "#1E1F1FFF", "#28FF1F1F",
        "#FFFF0000", "#FF808080", "#FFC0C000", "#FFEFFBBC", "#FF28320C"
    },
    { // Blue
        "#FF83A5D2", "#FFFFFFFA", "#FF3232D1", "#FF282828", "#FFF0F0F0", "#FF3333FF",
        "#A01F1FFF", "#A01FFF1F", "#501F1FFF", "#501FFF1F", "#371F1FFF", "#3C1FFF1F", "#1E1F1FFF", "#281FFF1F",
        "#FFFF0000", "#FF808080", "#FFC0C000", "#FFFFFF00", "#FF2E2B53"
    },
    { // Grey
        "#FF666666", "#FFDDDDDD", "#FFFF0000", "#FF000000", "#FFFFFFFF", "#FF888888",
        "#A01F1FFF", "#A0FF1F1F", "#501F1FFF", "#50FF1F1F", "#371F1FFF", "#3CFF1F1F", "#1E1F1FFF", "#28FF1F1F",
        "#FFFF0000", "#FF909090", "#FFC0C000", "#FFFFFFFF", "#FF202020"
    },
    { // Scid Default
        "#FF80A0A0", "#FFD0E0D0", "#FFFF0000", "#FF000000", "#FFFFFFFF", "#FF666666",
        "#A01F1FFF", "#A0FF1F1F", "#501F1FFF", "#50FF1F1F", "#371F1FFF", "#3CFF1F1F", "#1E1F1FFF", "#28FF1F1F",
        "#FFFF0000", "#FF808080", "#FFC0C000", "#FFDEFBDE", "#FF213429"
    },
    { // Scid Brown
        "#B58863",   "#F0D9B5",   "#FFFF0000", "#FF000000", "#FFFFFFFF", "#FF666666",
        "#A01F1FFF", "#A0FF1F1F", "#501F1FFF", "#50FF1F1F", "#371F1FFF", "#3CFF1F1F", "#1E1F1FFF", "#28FF1F1F",
        "#FFFF0000", "#FF808080", "#FFC0C000", "#FFF7FAE3", "#FF40260A"
    },
    { // Scid Green
        "#FF769656", "#FFEEEED2", "#FFFF0000", "#FF000000", "#FFFFFFFF", "#FF666666",
        "#A01F1FFF", "#A0FF1F1F", "#501F1FFF", "#50FF1F1F", "#371F1FFF", "#3CFF1F1F", "#1E1F1FFF", "#28FF1F1F",
        "#FFFF0000", "#FF808080", "#FFC0C000", "#FFDEE3CE", "#FF183C21"
    },
    { // Lichess Brown — warm wood-inspired flat colors
        "#FFB58863", "#FFF0D9B5", "#50FFFF00", "#FF1B1B1B", "#FFFAFAFA", "#80AAA23A",
        "#A01F1FFF", "#A0FF1F1F", "#501F1FFF", "#50FF1F1F", "#371F1FFF", "#3CFF1F1F", "#1E1F1FFF", "#28FF1F1F",
        "#FFCD5C5C", "#FF987654", "#FF8B7355", "#FFF5F0E8", "#FF302418"
    },
    { // Lichess Blue — cool blue, clean modern look
        "#FF8CA2AD", "#FFDEE3E6", "#5000A5FF", "#FF1C1C1C", "#FFF8F8F8", "#804A90D9",
        "#A01F1FFF", "#A01FFF1F", "#501F1FFF", "#501FFF1F", "#371F1FFF", "#3C1FFF1F", "#1E1F1FFF", "#281FFF1F",
        "#FF4A90D9", "#FF8097A5", "#FF5B8BA0", "#FFEDF2F4", "#FF1B2838"
    },
    { // Lichess Green — chess.com/lichess green
        "#FF779952", "#FFEDEED1", "#50FFFF00", "#FF1B1B1B", "#FFFAFAFA", "#8086A666",
        "#A01F1FFF", "#A0FF1F1F", "#501F1FFF", "#50FF1F1F", "#371F1FFF", "#3CFF1F1F", "#1E1F1FFF", "#28FF1F1F",
        "#FFCD5C5C", "#FF6D8B3A", "#FF5E7C2E", "#FFF0F4E4", "#FF1A2E12"
    },
    { // Midnight — dark mode
        "#FF3D4551", "#FF525F6E", "#50FFD700", "#FFDEDEDE", "#FF2A2A2A", "#80607080",
        "#A06495ED", "#A0FF6B6B", "#506495ED", "#50FF6B6B", "#376495ED", "#3CFF6B6B", "#1E6495ED", "#28FF6B6B",
        "#FFFFD700", "#FF607080", "#FF87CEEB", "#FFE0E0E0", "#FF1A1E24"
    },
    { // Lichess Purple
        "#FF7D4A8D", "#FF9F90B0", "#50DA70D6", "#FF1B1B1B", "#FFFAFAFA", "#80A050C8",
        "#A01F1FFF", "#A0FF1F1F", "#501F1FFF", "#50FF1F1F", "#371F1FFF", "#3CFF1F1F", "#1E1F1FFF", "#28FF1F1F",
        "#FFDA70D6", "#FF8B6E9B", "#FF9B59B6", "#FFF4EEF7", "#FF2D1A33"
    },
    { // Lichess Pink
        "#FFED7272", "#FFE8E9B7", "#50FF69B4", "#FF1B1B1B", "#FFFAFAFA", "#80E85080",
        "#A01F1FFF", "#A0FF1F1F", "#501F1FFF", "#50FF1F1F", "#371F1FFF", "#3CFF1F1F", "#1E1F1FFF", "#28FF1F1F",
        "#FFFF69B4", "#FFCC6666", "#FFD4637A", "#FFF9F0F0", "#FF331A1A"
    },
    { // Lichess IC — Internet Chess style
        "#FFC1C18E", "#FFECECEC", "#50FFFF00", "#FF1B1B1B", "#FFFAFAFA", "#80B0B060",
        "#A01F1FFF", "#A0FF1F1F", "#501F1FFF", "#50FF1F1F", "#371F1FFF", "#3CFF1F1F", "#1E1F1FFF", "#28FF1F1F",
        "#FFCD5C5C", "#FF9B9B6E", "#FF8B8B5E", "#FFF5F5E8", "#FF2A2A1A"
    },
    { // Lichess Wood — warm golden wood
        "#FF9B4D0F", "#FFD8A45B", "#50FFFF00", "#FF1B1B1B", "#FFFAFAFA", "#80C07020",
        "#A01F1FFF", "#A0FF1F1F", "#501F1FFF", "#50FF1F1F", "#371F1FFF", "#3CFF1F1F", "#1E1F1FFF", "#28FF1F1F",
        "#FFCD5C5C", "#FF8B6914", "#FF7A5A10", "#FFF5E8D0", "#FF301A05"
    },
    { // Lichess Canvas — blue-grey fabric
        "#FF547388", "#FFD7DAEB", "#5000A5FF", "#FF1C1C1C", "#FFF8F8F8", "#804A6E8A",
        "#A01F1FFF", "#A01FFF1F", "#501F1FFF", "#501FFF1F", "#371F1FFF", "#3C1FFF1F", "#1E1F1FFF", "#281FFF1F",
        "#FF4A90D9", "#FF6A8898", "#FF5B7888", "#FFEDF0F5", "#FF1B2530"
    },
    { // Lichess Marble — green marble
        "#FF4F644E", "#FF93AB91", "#50FFFF00", "#FF1B1B1B", "#FFFAFAFA", "#80608060",
        "#A01F1FFF", "#A0FF1F1F", "#501F1FFF", "#50FF1F1F", "#371F1FFF", "#3CFF1F1F", "#1E1F1FFF", "#28FF1F1F",
        "#FFCD5C5C", "#FF5A7A5A", "#FF4A6A4A", "#FFF0F5F0", "#FF1A2B1A"
    }
    };

    /** Default board texture for each theme (empty string = flat color). */
    private final static String[] themeBoardTexture = {
        "",             // Original
        "",             // XBoard
        "blue",         // Blue
        "grey",         // Grey
        "",             // Scid Default
        "brown",        // Scid Brown
        "green",        // Scid Green
        "brown",        // Lichess Brown
        "blue",         // Lichess Blue
        "green",        // Lichess Green
        "",             // Midnight
        "purple",       // Lichess Purple
        "",             // Lichess Pink
        "ic",           // Lichess IC
        "wood",         // Lichess Wood
        "canvas",       // Lichess Canvas
        "marble",       // Lichess Marble
    };

    /** Default piece set for each theme. */
    private final static String[] themePieceSet = {
        "chesscases",   // Original
        "chesscases",   // XBoard
        "cburnett",     // Blue
        "cburnett",     // Grey
        "merida",       // Scid Default
        "merida",       // Scid Brown
        "merida",       // Scid Green
        "cburnett",     // Lichess Brown
        "cburnett",     // Lichess Blue
        "cburnett",     // Lichess Green
        "cburnett",     // Midnight
        "cburnett",     // Lichess Purple
        "cburnett",     // Lichess Pink
        "icpieces",     // Lichess IC
        "cburnett",     // Lichess Wood
        "cburnett",     // Lichess Canvas
        "cburnett",     // Lichess Marble
    };

    final void readColors(SharedPreferences settings) {
        for (int i = 0; i < numColors; i++) {
            String prefName = prefPrefix + prefNames[i];
            String defaultColor = themeColors[defaultTheme][i];
            String colorString = settings.getString(prefName, defaultColor);
            colorTable[i] = 0;
            try {
                colorTable[i] = Color.parseColor(colorString);
            } catch (IllegalArgumentException|StringIndexOutOfBoundsException ignore) {
            }
        }
    }

    final void setTheme(SharedPreferences settings, int themeType) {
        Editor editor = settings.edit();
        for (int i = 0; i < numColors; i++)
            editor.putString(prefPrefix + prefNames[i], themeColors[themeType][i]);
        if (themeType < themeBoardTexture.length)
            editor.putString("boardTexture", themeBoardTexture[themeType]);
        if (themeType < themePieceSet.length)
            editor.putString("viewPieceSet", themePieceSet[themeType]);
        editor.apply();
        readColors(settings);
    }

    public static int getDefaultThemeIndex() {
        return defaultTheme;
    }

    public final int getColor(int colorType) {
        return colorTable[colorType];
    }
}
