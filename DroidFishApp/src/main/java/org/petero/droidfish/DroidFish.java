/*
    DroidFish - An Android chess program.
    Copyright (C) 2011-2014  Peter Österlund, peterosterlund2@gmail.com
    Copyright (C) 2012 Leo Mayer

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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.petero.droidfish.activities.CPUWarning;
import org.petero.droidfish.activities.EditBoard;
import org.petero.droidfish.activities.EditOptions;
import org.petero.droidfish.activities.EditPGNLoad;
import org.petero.droidfish.activities.EditPGNSave;
import org.petero.droidfish.activities.LoadFEN;
import org.petero.droidfish.activities.LoadScid;
import org.petero.droidfish.activities.util.PGNFile;
import org.petero.droidfish.activities.util.PGNFile.GameInfo;
import org.petero.droidfish.activities.Preferences;
import org.petero.droidfish.book.BookOptions;
import org.petero.droidfish.engine.DroidComputerPlayer.EloData;
import org.petero.droidfish.engine.EngineUtil;
import org.petero.droidfish.engine.UCIEngine;
import org.petero.droidfish.engine.UCIEngineBase;
import org.petero.droidfish.engine.UCIOptions;
import org.petero.droidfish.gamelogic.DroidChessController;
import org.petero.droidfish.gamelogic.ChessParseError;
import org.petero.droidfish.gamelogic.Game;
import org.petero.droidfish.gamelogic.Move;
import org.petero.droidfish.gamelogic.Position;
import org.petero.droidfish.gamelogic.TextIO;
import org.petero.droidfish.gamelogic.GameTree.Node;
import org.petero.droidfish.gamelogic.TimeControlData;
import org.petero.droidfish.enginematch.EngineMatchConfig;
import org.petero.droidfish.enginematch.EngineMatchController;
import org.petero.droidfish.enginematch.EngineMatchListener;
import org.petero.droidfish.enginematch.EngineMatchResult;
import org.petero.droidfish.tb.Probe;
import org.petero.droidfish.tb.ProbeResult;
import org.petero.droidfish.view.EvalBarView;
import org.petero.droidfish.view.MoveListView;
import org.petero.droidfish.view.ChessBoard.SquareDecoration;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.DialogFragment;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import tourguide.tourguide.Overlay;
import tourguide.tourguide.Pointer;
import tourguide.tourguide.Sequence;
import tourguide.tourguide.ToolTip;
import tourguide.tourguide.TourGuide;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.kalab.chess.enginesupport.ChessEngine;
import com.kalab.chess.enginesupport.ChessEngineResolver;

import android.Manifest;
import android.annotation.SuppressLint;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.StateListDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.drawerlayout.widget.DrawerLayout;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.os.PowerManager;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

@SuppressLint("ClickableViewAccessibility")
public class DroidFish extends AppCompatActivity
                       implements GUIInterface,
                                  ActivityCompat.OnRequestPermissionsResultCallback,
                                  DialogHost {
    private ChessBoardPlay cb;
    DroidChessController ctrl = null;
    private boolean mShowThinking;
    private boolean mShowStats;
    private boolean fullPVLines;
    private int numPV;
    private boolean mWhiteBasedScores;
    private boolean mShowBookHints;
    private int mEcoHints;
    private int maxNumArrows;
    private boolean mEvalBarHvE;
    private String mEvalBarAnalysis;
    private EvalBarView evalBar;
    GameMode gameMode;
    private boolean mPonderMode;
    private int timeControl;
    private int movesPerSession;
    private int timeIncrement;
    private String playerName;
    private boolean boardFlipped;
    private boolean autoSwapSides;
    private boolean playerNameFlip;
    private boolean discardVariations;

    private TextView status;
    private ScrollView moveListScroll;
    private MoveListView moveList;
    private View thinkingScroll;
    private TextView thinking;
    private View buttons;
    private ImageButton custom1Button, custom2Button, custom3Button;
    private ImageButton modeButton, undoButton, redoButton;
    private ImageButton adjudicateButton, stopMatchButton;
    private ButtonActions custom1ButtonActions, custom2ButtonActions, custom3ButtonActions;
    private TextView whiteTitleText, blackTitleText, engineTitleText;
    private View secondTitleLine;
    private TextView whiteFigText, blackFigText, summaryTitleText;
    private Dialog moveListMenuDlg;

    private DrawerLayout drawerLayout;
    private ListView leftDrawer;
    private ListView rightDrawer;

    private EngineMatchController engineMatchController;
    private TextView matchScoreLine;
    private StringBuilder matchMoveText;

    private SharedPreferences settings;
    private ObjectCache cache;

    boolean dragMoveEnabled;
    float scrollSensitivity;
    boolean invertScrollDirection;
    boolean scrollGames;
    private boolean autoScrollMoveList;

    private boolean leftHanded;
    private String moveAnnounceType;
    private boolean moveSoundEnabled;
    private MediaPlayer moveSound;
    private boolean vibrateEnabled;
    private boolean animateMoves;
    private boolean autoScrollTitle;
    private boolean showVariationLine;

    private int autoMoveDelay; // Delay in auto forward/backward mode
    enum AutoMode {
        OFF, FORWARD, BACKWARD
    }
    private AutoMode autoMode = AutoMode.OFF;

    private int ECO_HINTS_OFF = 0;
    private int ECO_HINTS_AUTO = 1;
    private int ECO_HINTS_ALWAYS = 2;

    /** State of requested permissions. */
    private enum PermissionState {
        UNKNOWN,
        REQUESTED,
        GRANTED,
        DENIED
    }
    /** State of WRITE_EXTERNAL_STORAGE permission. */
    private PermissionState storagePermission = PermissionState.UNKNOWN;

    private static String bookDir = "book";
    private static String pgnDir = "pgn";
    private static String fenDir = "epd";
    private static String engineDir = "uci";
    private static String engineLogDir = "uci/logs";
    private static String gtbDefaultDir = "gtb";
    private static String rtbDefaultDir = "rtb";
    private BookOptions bookOptions = new BookOptions();
    private PGNOptions pgnOptions = new PGNOptions();
    private EngineOptions engineOptions = new EngineOptions();

    private long lastVisibleMillis; // Time when GUI became invisible. 0 if currently visible.
    private long lastComputationMillis; // Time when engine last showed that it was computing.

    private PgnScreenText gameTextListener;

    private Typeface figNotation;
    private Typeface defaultThinkingListTypeFace;

    private boolean guideShowOnStart;
    private TourGuide tourGuide;

    private Speech speech;


    /** Defines all configurable button actions. */
    ActionFactory actionFactory = new ActionFactory() {
        private HashMap<String, UIAction> actions;

        private void addAction(UIAction a) {
            actions.put(a.getId(), a);
        }

        {
            actions = new HashMap<>();
            addAction(new UIAction() {
                public String getId() { return "flipboard"; }
                public int getName() { return R.string.flip_board; }
                public int getIcon() { return R.raw.flip; }
                public boolean enabled() { return true; }
                public void run() {
                    boardFlipped = !cb.flipped;
                    setBooleanPref("boardFlipped", boardFlipped);
                    cb.setFlipped(boardFlipped);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "showThinking"; }
                public int getName() { return R.string.toggle_show_thinking; }
                public int getIcon() { return R.raw.thinking; }
                public boolean enabled() { return true; }
                public void run() {
                    mShowThinking = toggleBooleanPref("showThinking");
                    updateThinkingInfo();
                }
            });
            addAction(new UIAction() {
                public String getId() { return "bookHints"; }
                public int getName() { return R.string.toggle_book_hints; }
                public int getIcon() { return R.raw.book; }
                public boolean enabled() { return true; }
                public void run() {
                    mShowBookHints = toggleBooleanPref("bookHints");
                    updateThinkingInfo();
                }
            });
            addAction(new UIAction() {
                public String getId() { return "tbHints"; }
                public int getName() { return R.string.toggle_tb_hints; }
                public int getIcon() { return R.raw.tb; }
                public boolean enabled() { return true; }
                public void run() {
                    engineOptions.hints = toggleBooleanPref("tbHints");
                    setEgtbHints(cb.getSelectedSquare());
                }
            });
            addAction(new UIAction() {
                public String getId() { return "viewVariations"; }
                public int getName() { return R.string.toggle_pgn_variations; }
                public int getIcon() { return R.raw.variation; }
                public boolean enabled() { return true; }
                public void run() {
                    pgnOptions.view.variations = toggleBooleanPref("viewVariations");
                    gameTextListener.clear();
                    ctrl.prefsChanged(false);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "viewComments"; }
                public int getName() { return R.string.toggle_pgn_comments; }
                public int getIcon() { return R.raw.comment; }
                public boolean enabled() { return true; }
                public void run() {
                    pgnOptions.view.comments = toggleBooleanPref("viewComments");
                    gameTextListener.clear();
                    ctrl.prefsChanged(false);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "viewHeaders"; }
                public int getName() { return R.string.toggle_pgn_headers; }
                public int getIcon() { return R.raw.header; }
                public boolean enabled() { return true; }
                public void run() {
                    pgnOptions.view.headers = toggleBooleanPref("viewHeaders");
                    gameTextListener.clear();
                    ctrl.prefsChanged(false);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "toggleAnalysis"; }
                public int getName() { return R.string.toggle_analysis; }
                public int getIcon() { return R.raw.analyze; }
                public boolean enabled() { return true; }
                private int oldGameModeType = GameMode.EDIT_GAME;
                public void run() {
                    int gameModeType;
                    if (ctrl.analysisMode()) {
                        gameModeType = oldGameModeType;
                    } else {
                        oldGameModeType = ctrl.getGameMode().getModeNr();
                        gameModeType = GameMode.ANALYSIS;
                    }
                    newGameMode(gameModeType);
                    setBoardFlip(false);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "forceMove"; }
                public int getName() { return R.string.option_force_computer_move; }
                public int getIcon() { return R.raw.stop; }
                public boolean enabled() { return true; }
                public void run() {
                    ctrl.stopSearch();
                }
            });
            addAction(new UIAction() {
                public String getId() { return "largeButtons"; }
                public int getName() { return R.string.toggle_large_buttons; }
                public int getIcon() { return R.raw.magnify; }
                public boolean enabled() { return true; }
                public void run() {
                    toggleBooleanPref("largeButtons");
                    updateButtons();
                }
            });
            addAction(new UIAction() {
                public String getId() { return "blindMode"; }
                public int getName() { return R.string.blind_mode; }
                public int getIcon() { return R.raw.blind; }
                public boolean enabled() { return true; }
                public void run() {
                    boolean blindMode = !cb.blindMode;
                    setBooleanPref("blindMode", blindMode);
                    cb.setBlindMode(blindMode);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "loadLastFile"; }
                public int getName() { return R.string.load_last_file; }
                public int getIcon() { return R.raw.open_last_file; }
                public boolean enabled() { return currFileType() != FT_NONE && storageAvailable(); }
                public void run() {
                    loadLastFile();
                }
            });
            addAction(new UIAction() {
                public String getId() { return "loadGame"; }
                public int getName() { return R.string.load_game; }
                public int getIcon() { return R.raw.open_file; }
                public boolean enabled() { return storageAvailable(); }
                public void run() {
                    selectFile(R.string.select_pgn_file, R.string.pgn_load, "currentPGNFile", pgnDir,
                               SELECT_PGN_FILE_DIALOG, RESULT_OI_PGN_LOAD);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "selectEngine"; }
                public int getName() { return R.string.select_engine; }
                public int getIcon() { return R.raw.engine; }
                public boolean enabled() { return true; }
                public void run() {
                    reShowDialog(SELECT_ENGINE_DIALOG_NOMANAGE);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "engineOptions"; }
                public int getName() { return R.string.engine_options; }
                public int getIcon() { return R.raw.custom; }
                public boolean enabled() { return canSetEngineOptions(); }
                public void run() {
                    setEngineOptions();
                }
            });
            addAction(new UIAction() {
                public String getId() { return "toggleArrows"; }
                public int getName() { return R.string.toggle_arrows; }
                public int getIcon() { return R.raw.custom; }
                public boolean enabled() { return true; }
                public void run() {
                    String numArrows = settings.getString("thinkingArrows", "4");
                    Editor editor = settings.edit();
                    if (!"0".equals(numArrows)) {
                        editor.putString("thinkingArrows", "0");
                        editor.putString("oldThinkingArrows", numArrows);
                    } else {
                        String oldNumArrows = settings.getString("oldThinkingArrows", "0");
                        if ("0".equals(oldNumArrows))
                            oldNumArrows = "4";
                        editor.putString("thinkingArrows", oldNumArrows);
                    }
                    editor.apply();
                    maxNumArrows = getIntSetting("thinkingArrows", 4);
                    updateThinkingInfo();
                }
            });
            addAction(new UIAction() {
                public String getId() { return "prevGame"; }
                public int getName() { return R.string.load_prev_game; }
                public int getIcon() { return R.raw.custom; }
                public boolean enabled() {
                    return (currFileType() != FT_NONE) && !gameMode.clocksActive();
                }
                public void run() {
                    final int currFT = currFileType();
                    final String currPathName = currPathName();
                    Intent i;
                    if (currFT == FT_PGN) {
                        i = new Intent(DroidFish.this, EditPGNLoad.class);
                        i.setAction("org.petero.droidfish.loadFilePrevGame");
                        i.putExtra("org.petero.droidfish.pathname", currPathName);
                        startActivityForResult(i, RESULT_LOAD_PGN);
                    } else if (currFT == FT_SCID) {
                        i = new Intent(DroidFish.this, LoadScid.class);
                        i.setAction("org.petero.droidfish.loadScidPrevGame");
                        i.putExtra("org.petero.droidfish.pathname", currPathName);
                        startActivityForResult(i, RESULT_LOAD_PGN);
                    } else if (currFT == FT_FEN) {
                        i = new Intent(DroidFish.this, LoadFEN.class);
                        i.setAction("org.petero.droidfish.loadPrevFen");
                        i.putExtra("org.petero.droidfish.pathname", currPathName);
                        startActivityForResult(i, RESULT_LOAD_FEN);
                    }
                }
            });
            addAction(new UIAction() {
                public String getId() { return "nextGame"; }
                public int getName() { return R.string.load_next_game; }
                public int getIcon() { return R.raw.custom; }
                public boolean enabled() {
                    return (currFileType() != FT_NONE) && !gameMode.clocksActive();
                }
                public void run() {
                    final int currFT = currFileType();
                    final String currPathName = currPathName();
                    Intent i;
                    if (currFT == FT_PGN) {
                        i = new Intent(DroidFish.this, EditPGNLoad.class);
                        i.setAction("org.petero.droidfish.loadFileNextGame");
                        i.putExtra("org.petero.droidfish.pathname", currPathName);
                        startActivityForResult(i, RESULT_LOAD_PGN);
                    } else if (currFT == FT_SCID) {
                        i = new Intent(DroidFish.this, LoadScid.class);
                        i.setAction("org.petero.droidfish.loadScidNextGame");
                        i.putExtra("org.petero.droidfish.pathname", currPathName);
                        startActivityForResult(i, RESULT_LOAD_PGN);
                    } else if (currFT == FT_FEN) {
                        i = new Intent(DroidFish.this, LoadFEN.class);
                        i.setAction("org.petero.droidfish.loadNextFen");
                        i.putExtra("org.petero.droidfish.pathname", currPathName);
                        startActivityForResult(i, RESULT_LOAD_FEN);
                    }
                }
            });
        }

        @Override
        public UIAction getAction(String actionId) {
            return actions.get(actionId);
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerSAFLaunchers();

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        String intentPgnOrFen = null;
        String intentFilename = null;
        if (savedInstanceState == null) {
            Pair<String,String> pair = getPgnOrFenIntent();
            intentPgnOrFen = pair.first;
            intentFilename = pair.second;
        }

        StorageMigrationHelper.migrateIfNeeded(this);
        createDirectories();

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        if (!settings.contains("color_darkSquare")) {
            ColorTheme.instance().setTheme(settings, ColorTheme.getDefaultThemeIndex());
        }
        cache = new ObjectCache();

        setWakeLock(false);

        custom1ButtonActions = new ButtonActions("custom1", CUSTOM1_BUTTON_DIALOG,
                                                 R.string.select_action);
        custom2ButtonActions = new ButtonActions("custom2", CUSTOM2_BUTTON_DIALOG,
                                                 R.string.select_action);
        custom3ButtonActions = new ButtonActions("custom3", CUSTOM3_BUTTON_DIALOG,
                                                 R.string.select_action);

        figNotation = Typeface.createFromAsset(getAssets(), "fonts/DroidFishChessNotationDark.otf");
        setPieceNames(PGNOptions.PT_LOCAL);
        initUI();

        gameTextListener = new PgnScreenText(this, pgnOptions);
        moveList.setOnLinkClickListener(gameTextListener);
        if (ctrl != null)
            ctrl.shutdownEngine();
        ctrl = new DroidChessController(this, gameTextListener, pgnOptions);
        egtbForceReload = true;
        if (speech == null)
            speech = new Speech();
        readPrefs(false);
        TimeControlData tcData = new TimeControlData();
        tcData.setTimeControl(timeControl, movesPerSession, timeIncrement);
        ctrl.newGame(gameMode, tcData);
        setAutoMode(AutoMode.OFF);
        {
            byte[] data = null;
            int version = 1;
            if (savedInstanceState != null) {
                byte[] token = savedInstanceState.getByteArray("gameStateT");
                if (token != null)
                    data = cache.retrieveBytes(token);
                version = savedInstanceState.getInt("gameStateVersion", version);
            } else {
                String dataStr = settings.getString("gameState", null);
                version = settings.getInt("gameStateVersion", version);
                if (dataStr != null)
                    data = strToByteArr(dataStr);
            }
            if (data != null)
                ctrl.fromByteArray(data, version);
        }
        ctrl.setGuiPaused(true);
        ctrl.setGuiPaused(false);
        ctrl.startGame();
        if (intentPgnOrFen != null) {
            try {
                ctrl.setFENOrPGN(intentPgnOrFen, true);
                setBoardFlip(true);
            } catch (ChessParseError e) {
                // If FEN corresponds to illegal chess position, go into edit board mode.
                try {
                    TextIO.readFEN(intentPgnOrFen);
                } catch (ChessParseError e2) {
                    if (e2.pos != null)
                        startEditBoard(TextIO.toFEN(e2.pos));
                }
            }
        } else if (intentFilename != null) {
            if (intentFilename.toLowerCase(Locale.US).endsWith(".fen") ||
                intentFilename.toLowerCase(Locale.US).endsWith(".epd"))
                loadFENFromFile(intentFilename);
            else
                loadPGNFromFile(intentFilename);
        }

        startTourGuide();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(DroidFishApp.setLanguage(newBase, false));
    }

    private void startTourGuide(){
        if (!guideShowOnStart)
            return;

        tourGuide = TourGuide.init(this);
        ArrayList<TourGuide> guides = new ArrayList<>();

        TourGuide tg = TourGuide.init(this);
        tg.setToolTip(new ToolTip()
                      .setTitle(getString(R.string.tour_leftMenu_title))
                      .setDescription(getString(R.string.tour_leftMenu_desc))
                      .setGravity(Gravity.BOTTOM | Gravity.RIGHT));
        tg.playLater(whiteTitleText);
        guides.add(tg);

        tg = TourGuide.init(this);
        tg.setToolTip(new ToolTip()
                      .setTitle(getString(R.string.tour_rightMenu_title))
                      .setDescription(getString(R.string.tour_rightMenu_desc))
                      .setGravity(Gravity.BOTTOM | Gravity.LEFT));
        tg.playLater(blackTitleText);
        guides.add(tg);

        tg = TourGuide.init(this);
        int gravity = !landScapeView() ? Gravity.BOTTOM : leftHandedView() ? Gravity.LEFT : Gravity.RIGHT;
        tg.setToolTip(new ToolTip()
                      .setTitle(getString(R.string.tour_chessBoard_title))
                      .setDescription(getString(R.string.tour_chessBoard_desc))
                      .setGravity(gravity));
        tg.playLater(cb);
        guides.add(tg);

        tg = TourGuide.init(this);
        gravity = !landScapeView() ? Gravity.TOP : Gravity.BOTTOM;
        tg.setToolTip(new ToolTip()
                      .setTitle(getString(R.string.tour_buttons_title))
                      .setDescription(getString(R.string.tour_buttons_desc))
                      .setGravity(gravity));
        tg.playLater(buttons);
        guides.add(tg);

        tg = TourGuide.init(this);
        gravity = !landScapeView() ? Gravity.TOP : leftHandedView() ? Gravity.RIGHT : Gravity.LEFT;
        tg.setToolTip(new ToolTip()
                      .setTitle(getString(R.string.tour_moveList_title))
                      .setDescription(getString(R.string.tour_moveList_desc))
                      .setGravity(gravity));
        tg.playLater(moveListScroll);
        guides.add(tg);

        tg = TourGuide.init(this);
        tg.setToolTip(new ToolTip()
                      .setTitle(getString(R.string.tour_analysis_title))
                      .setDescription(getString(R.string.tour_analysis_desc))
                      .setGravity(Gravity.TOP));
        tg.playLater(thinkingScroll);
        guides.add(tg);

        tg.setOverlay(new Overlay()
                      .setOnClickListener(v -> {
                          guideShowOnStart = false;
                          Editor editor = settings.edit();
                          editor.putBoolean("guideShowOnStart", false);
                          editor.apply();
                          if (tourGuide != null) {
                              tourGuide.next();
                              tourGuide = null;
                          }
                      }));

        Sequence sequence = new Sequence.SequenceBuilder()
                .add(guides.toArray(new TourGuide[0]))
                .setDefaultOverlay(new Overlay()
                                   .setOnClickListener(v -> {
                                       if (tourGuide != null)
                                           tourGuide.next();
                                   }))
                .setDefaultPointer(new Pointer())
                .setContinueMethod(Sequence.ContinueMethod.OverlayListener)
                .build();
        tourGuide.playInSequence(sequence);
    }

    // Unicode code points for chess pieces
    private static final String figurinePieceNames = PieceFontInfo.NOTATION_PAWN   + " " +
                                                     PieceFontInfo.NOTATION_KNIGHT + " " +
                                                     PieceFontInfo.NOTATION_BISHOP + " " +
                                                     PieceFontInfo.NOTATION_ROOK   + " " +
                                                     PieceFontInfo.NOTATION_QUEEN  + " " +
                                                     PieceFontInfo.NOTATION_KING;

    private void setPieceNames(int pieceType) {
        if (pieceType == PGNOptions.PT_FIGURINE) {
            TextIO.setPieceNames(figurinePieceNames);
        } else {
            TextIO.setPieceNames(getString(R.string.piece_names));
        }
    }

    /** Create directory structure for app storage. */
    private void createDirectories() {
        if (Build.VERSION.SDK_INT < 30) {
            if (storagePermission == PermissionState.UNKNOWN) {
                String extStorage = Manifest.permission.WRITE_EXTERNAL_STORAGE;
                if (ContextCompat.checkSelfPermission(this, extStorage) ==
                        PackageManager.PERMISSION_GRANTED) {
                    storagePermission = PermissionState.GRANTED;
                } else {
                    storagePermission = PermissionState.REQUESTED;
                    ActivityCompat.requestPermissions(this, new String[]{extStorage}, 0);
                }
            }
            if (storagePermission != PermissionState.GRANTED)
                return;
        }
        StorageProvider.createDirectories();
        new File(StorageProvider.getEngineDir(), EngineUtil.openExchangeDir).mkdirs();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (storagePermission == PermissionState.REQUESTED) {
            if ((results.length > 0) && (results[0] == PackageManager.PERMISSION_GRANTED))
                storagePermission = PermissionState.GRANTED;
            else
                storagePermission = PermissionState.DENIED;
        }
        createDirectories();
    }

    /** Return true if file storage is available for the app. */
    private boolean storageAvailable() {
        if (Build.VERSION.SDK_INT >= 30)
            return true;
        return storagePermission == PermissionState.GRANTED;
    }

    /**
     * Return PGN/FEN data or filename from the Intent. Both can not be non-null.
     * @return Pair of PGN/FEN data and filename.
     */
    private Pair<String,String> getPgnOrFenIntent() {
        String pgnOrFen = null;
        String filename = null;
        try {
            Intent intent = getIntent();
            Uri data = intent.getData();
            if (data == null) {
                Bundle b = intent.getExtras();
                if (b != null) {
                    Object strm = b.get(Intent.EXTRA_STREAM);
                    if (strm instanceof Uri) {
                        data = (Uri)strm;
                        if ("file".equals(data.getScheme())) {
                            filename = data.getEncodedPath();
                            if (filename != null)
                                filename = Uri.decode(filename);
                        }
                    }
                }
            }
            if (data == null) {
                if ((Intent.ACTION_SEND.equals(intent.getAction()) ||
                     Intent.ACTION_VIEW.equals(intent.getAction())) &&
                    ("application/x-chess-pgn".equals(intent.getType()) ||
                     "application/x-chess-fen".equals(intent.getType())))
                    pgnOrFen = intent.getStringExtra(Intent.EXTRA_TEXT);
            } else {
                String scheme = data.getScheme();
                if ("file".equals(scheme)) {
                    filename = data.getEncodedPath();
                    if (filename != null)
                        filename = Uri.decode(filename);
                }
                if ((filename == null) &&
                    ("content".equals(scheme) || "file".equals(scheme))) {
                    ContentResolver resolver = getContentResolver();
                    String fn = new File(StorageProvider.getPgnDir(), ".sharedfile.pgn").getAbsolutePath();
                    try (InputStream in = resolver.openInputStream(data)) {
                        if (in == null)
                            throw new IOException("No input stream");
                        FileUtil.writeFile(in, fn);
                    }
                    PGNFile pgnFile = new PGNFile(fn);
                    long fileLen = FileUtil.getFileLength(fn);
                    boolean moreThanOneGame = false;
                    try {
                        ArrayList<GameInfo> gi = pgnFile.getGameInfo(2);
                        moreThanOneGame = gi.size() > 1;
                    } catch (IOException ignore) {
                    }
                    if (fileLen > 1024 * 1024 || moreThanOneGame) {
                        filename = fn;
                    } else {
                        try (FileInputStream in = new FileInputStream(fn)) {
                            pgnOrFen = FileUtil.readFromStream(in);
                        }
                    }
                }
            }
        } catch (IOException e) {
            DroidFishApp.toast(R.string.failed_to_read_pgn_data, Toast.LENGTH_SHORT);
        } catch (SecurityException|IllegalArgumentException e) {
            DroidFishApp.toast(e.getMessage(), Toast.LENGTH_LONG);
        }
        return new Pair<>(pgnOrFen,filename);
    }

    private byte[] strToByteArr(String str) {
        if (str == null)
            return null;
        int nBytes = str.length() / 2;
        byte[] ret = new byte[nBytes];
        for (int i = 0; i < nBytes; i++) {
            int c1 = str.charAt(i * 2) - 'A';
            int c2 = str.charAt(i * 2 + 1) - 'A';
            ret[i] = (byte)(c1 * 16 + c2);
        }
        return ret;
    }

    private String byteArrToString(byte[] data) {
        if (data == null)
            return null;
        StringBuilder ret = new StringBuilder(32768);
        for (int b : data) {
            if (b < 0) b += 256;
            char c1 = (char)('A' + (b / 16));
            char c2 = (char)('A' + (b & 15));
            ret.append(c1);
            ret.append(c2);
        }
        return ret.toString();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        reInitUI();
    }

    /** Re-initialize UI when layout should change because of rotation or handedness change. */
    private void reInitUI() {
        ChessBoardPlay oldCB = cb;
        String statusStr = status.getText().toString();
        initUI();
        readPrefs(true);
        cb.setPosition(oldCB.pos);
        cb.setFlipped(oldCB.flipped);
        cb.setDrawSquareLabels(oldCB.drawSquareLabels);
        cb.oneTouchMoves = oldCB.oneTouchMoves;
        cb.toggleSelection = oldCB.toggleSelection;
        cb.highlightLastMove = oldCB.highlightLastMove;
        cb.setBlindMode(oldCB.blindMode);
        setSelection(oldCB.selectedSquare);
        cb.userSelectedSquare = oldCB.userSelectedSquare;
        setStatusString(statusStr);
        moveList.setOnLinkClickListener(gameTextListener);
        moveListUpdated();
        updateThinkingInfo();
        ctrl.updateRemainingTime();
        ctrl.updateMaterialDiffList();
        if (tourGuide != null) {
            tourGuide.cleanUp();
            tourGuide = null;
        }
    }

    /** Return true if the current orientation is landscape. */
    private boolean landScapeView() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }
    
    /** Return true if left-handed layout should be used. */
    private boolean leftHandedView() {
        return settings.getBoolean("leftHanded", false) && landScapeView();
    }

    /** Re-read preferences settings. */
    private void handlePrefsChange() {
        if (leftHanded != leftHandedView())
            reInitUI();
        else
            readPrefs(true);
        maybeAutoModeOff(gameMode);
        ctrl.setGameMode(gameMode);
    }

    private void initUI() {
        leftHanded = leftHandedView();
        setContentView(leftHanded ? R.layout.main_left_handed : R.layout.main);

        View mainView = findViewById(R.id.main);
        View leftContainer = findViewById(R.id.left_drawer_container);
        View rightContainer = findViewById(R.id.right_drawer_container);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.drawer_layout), (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars());
            mainView.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            if (leftContainer != null)
                leftContainer.setPadding(0, insets.top, 0, insets.bottom);
            if (rightContainer != null)
                rightContainer.setPadding(0, insets.top, 0, insets.bottom);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });

        overrideViewAttribs();

        // title lines need to be regenerated every time due to layout changes (rotations)
        View firstTitleLine = findViewById(R.id.first_title_line);
        secondTitleLine = findViewById(R.id.second_title_line);
        whiteTitleText = findViewById(R.id.white_clock);
        whiteTitleText.setSelected(true);
        blackTitleText = findViewById(R.id.black_clock);
        blackTitleText.setSelected(true);
        engineTitleText = findViewById(R.id.title_text);
        whiteFigText = findViewById(R.id.white_pieces);
        whiteFigText.setTypeface(figNotation);
        whiteFigText.setSelected(true);
        whiteFigText.setTextColor(whiteTitleText.getTextColors());
        blackFigText = findViewById(R.id.black_pieces);
        blackFigText.setTypeface(figNotation);
        blackFigText.setSelected(true);
        blackFigText.setTextColor(blackTitleText.getTextColors());
        summaryTitleText = findViewById(R.id.title_text_summary);

        status = findViewById(R.id.status);
        moveListScroll = findViewById(R.id.scrollView);
        moveList = findViewById(R.id.moveList);
        thinkingScroll = findViewById(R.id.scrollViewBot);
        thinking = findViewById(R.id.thinking);
        defaultThinkingListTypeFace = thinking.getTypeface();
        status.setFocusable(false);
        moveListScroll.setFocusable(false);
        moveList.setFocusable(false);
        thinking.setFocusable(false);

        initDrawers();

        class ClickListener implements OnClickListener, OnTouchListener {
            private float touchX = -1;
            @Override
            public void onClick(View v) {
                boolean left = touchX <= v.getWidth() / 2.0;
                drawerLayout.openDrawer(left ? Gravity.LEFT : Gravity.RIGHT);
                touchX = -1;
            }

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                touchX = event.getX();
                return false;
            }
        }
        ClickListener listener = new ClickListener();
        firstTitleLine.setOnClickListener(listener);
        firstTitleLine.setOnTouchListener(listener);
        secondTitleLine.setOnClickListener(listener);
        secondTitleLine.setOnTouchListener(listener);

        cb = findViewById(R.id.chessboard);
        cb.setFocusable(true);
        cb.requestFocus();
        cb.setClickable(true);
        cb.setPgnOptions(pgnOptions);

        evalBar = findViewById(R.id.eval_bar);

        ChessBoardPlayListener cbpListener = new ChessBoardPlayListener(this, cb);
        cb.setOnTouchListener(cbpListener);

        moveList.setOnLongClickListener(v -> {
            reShowDialog(MOVELIST_MENU_DIALOG);
            return true;
        });
        thinking.setOnLongClickListener(v -> {
            if (mShowThinking || gameMode.analysisMode())
                if (!pvMoves.isEmpty())
                    reShowDialog(THINKING_MENU_DIALOG);
            return true;
        });

        buttons = findViewById(R.id.buttons);
        custom1Button = findViewById(R.id.custom1Button);
        custom1ButtonActions.setImageButton(custom1Button, this);
        custom2Button = findViewById(R.id.custom2Button);
        custom2ButtonActions.setImageButton(custom2Button, this);
        custom3Button = findViewById(R.id.custom3Button);
        custom3ButtonActions.setImageButton(custom3Button, this);

        modeButton = findViewById(R.id.modeButton);
        modeButton.setOnClickListener(v -> showDroidFishDialog(GAME_MODE_DIALOG));
        modeButton.setOnLongClickListener(v -> {
            drawerLayout.openDrawer(Gravity.LEFT);
            return true;
        });
        undoButton = findViewById(R.id.undoButton);
        undoButton.setOnClickListener(v -> {
            setAutoMode(AutoMode.OFF);
            ctrl.undoMove();
        });
        undoButton.setOnLongClickListener(v -> {
            reShowDialog(GO_BACK_MENU_DIALOG);
            return true;
        });
        redoButton = findViewById(R.id.redoButton);
        redoButton.setOnClickListener(v -> {
            setAutoMode(AutoMode.OFF);
            ctrl.redoMove();
        });
        redoButton.setOnLongClickListener(v -> {
            reShowDialog(GO_FORWARD_MENU_DIALOG);
            return true;
        });

        adjudicateButton = findViewById(R.id.adjudicateButton);
        adjudicateButton.setOnClickListener(v -> showAdjudicateDialog());

        stopMatchButton = findViewById(R.id.stopMatchButton);
        stopMatchButton.setOnClickListener(v -> {
            if (engineMatchController != null && engineMatchController.isRunning()) {
                engineMatchController.stopMatch();
                setEngineMatchMode(false, false);
            }
        });

        matchScoreLine = findViewById(R.id.match_score_line);
    }

    private static final int serializeVersion = 4;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (ctrl != null) {
            byte[] data = ctrl.toByteArray();
            byte[] token = data == null ? null : cache.storeBytes(data);
            outState.putByteArray("gameStateT", token);
            outState.putInt("gameStateVersion", serializeVersion);
        }
    }

    @Override
    protected void onResume() {
        lastVisibleMillis = 0;
        if (ctrl != null)
            ctrl.setGuiPaused(false);
        notificationActive = true;
        updateNotification();
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (ctrl != null) {
            setAutoMode(AutoMode.OFF);
            ctrl.setGuiPaused(true);
            byte[] data = ctrl.toByteArray();
            Editor editor = settings.edit();
            String dataStr = byteArrToString(data);
            editor.putString("gameState", dataStr);
            editor.putInt("gameStateVersion", serializeVersion);
            editor.apply();
        }
        lastVisibleMillis = System.currentTimeMillis();
        updateNotification();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        setAutoMode(AutoMode.OFF);
        if (ctrl != null)
            ctrl.shutdownEngine();
        setNotification(false);
        if (speech != null)
            speech.shutdown();
        super.onDestroy();
    }

    private int getIntSetting(String settingName, int defaultValue) {
        String tmp = settings.getString(settingName, String.format(Locale.US, "%d", defaultValue));
        return Integer.parseInt(tmp);
    }

    private void readPrefs(boolean restartIfLangChange) {
        int modeNr = getIntSetting("gameMode", 1);
        gameMode = new GameMode(modeNr);
        String oldPlayerName = playerName;
        playerName = settings.getString("playerName", "Player");
        boardFlipped = settings.getBoolean("boardFlipped", false);
        autoSwapSides = settings.getBoolean("autoSwapSides", false);
        playerNameFlip = settings.getBoolean("playerNameFlip", true);
        setBoardFlip(!playerName.equals(oldPlayerName));
        boolean drawSquareLabels = settings.getBoolean("drawSquareLabels", false);
        cb.setDrawSquareLabels(drawSquareLabels);
        cb.oneTouchMoves = settings.getBoolean("oneTouchMoves", false);
        cb.toggleSelection = getIntSetting("squareSelectType", 0) == 1;
        cb.highlightLastMove = settings.getBoolean("highlightLastMove", true);
        cb.setBlindMode(settings.getBoolean("blindMode", false));

        mShowThinking = settings.getBoolean("showThinking", false);
        mShowStats = settings.getBoolean("showStats", true);
        fullPVLines = settings.getBoolean("fullPVLines", false);
        numPV = settings.getInt("numPV", 1);
        ctrl.setMultiPVMode(numPV);
        mWhiteBasedScores = settings.getBoolean("whiteBasedScores", false);
        maxNumArrows = getIntSetting("thinkingArrows", 4);
        mShowBookHints = settings.getBoolean("bookHints", false);
        mEvalBarHvE = settings.getBoolean("evalBarHvE", false);
        mEvalBarAnalysis = settings.getString("evalBarAnalysis", "textOnly");
        updateEvalBarVisibility();
        mEcoHints = getIntSetting("ecoHints", ECO_HINTS_AUTO);

        String engine = settings.getString("engine", "stockfish");
        setEngine(engine);

        mPonderMode = settings.getBoolean("ponderMode", false);
        if (!mPonderMode)
            ctrl.stopPonder();

        timeControl = getIntSetting("timeControl", 120000);
        movesPerSession = getIntSetting("movesPerSession", 60);
        timeIncrement = getIntSetting("timeIncrement", 0);

        autoMoveDelay = getIntSetting("autoDelay", 5000);

        dragMoveEnabled = settings.getBoolean("dragMoveEnabled", true);
        scrollSensitivity = Float.parseFloat(settings.getString("scrollSensitivity", "2"));
        invertScrollDirection = settings.getBoolean("invertScrollDirection", false);
        scrollGames = settings.getBoolean("scrollGames", false);
        autoScrollMoveList = settings.getBoolean("autoScrollMoveList", true);
        discardVariations = settings.getBoolean("discardVariations", false);
        Util.setFullScreenMode(this, settings);
        boolean useWakeLock = settings.getBoolean("wakeLock", false);
        setWakeLock(useWakeLock);

        DroidFishApp.setLanguage(this, restartIfLangChange);
        int fontSize = getIntSetting("fontSize", 12);
        int statusFontSize = fontSize;
        Configuration config = getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_PORTRAIT)
            statusFontSize = Math.min(statusFontSize, 16);
        status.setTextSize(statusFontSize);
        moveAnnounceType = settings.getString("moveAnnounceType", "off");
        moveSoundEnabled = settings.getBoolean("moveSoundEnabled", false);
        if (moveAnnounceType.equals("sound")) {
            moveAnnounceType = "off";
            moveSoundEnabled = true;
            Editor editor = settings.edit();
            editor.putString("moveAnnounceType", moveAnnounceType);
            editor.putBoolean("moveSoundEnabled", moveSoundEnabled);
            editor.apply();
        }
        initSpeech();
        vibrateEnabled = settings.getBoolean("vibrateEnabled", false);
        animateMoves = settings.getBoolean("animateMoves", true);
        autoScrollTitle = settings.getBoolean("autoScrollTitle", true);
        setTitleScrolling();

        custom1ButtonActions.readPrefs(settings, actionFactory);
        custom2ButtonActions.readPrefs(settings, actionFactory);
        custom3ButtonActions.readPrefs(settings, actionFactory);
        updateButtons();

        guideShowOnStart = settings.getBoolean("guideShowOnStart", true);

        bookOptions.filename = settings.getString("bookFile", "");
        bookOptions.maxLength = getIntSetting("bookMaxLength", 1000000);
        bookOptions.preferMainLines = settings.getBoolean("bookPreferMainLines", false);
        bookOptions.tournamentMode = settings.getBoolean("bookTournamentMode", false);
        bookOptions.random = (settings.getInt("bookRandom", 500) - 500) * (3.0 / 500);
        setBookOptions();

        engineOptions.hashMB = getIntSetting("hashMB", 16);
        engineOptions.unSafeHash = new File(StorageProvider.getEngineDir(), ".unsafehash").exists();
        engineOptions.hints = settings.getBoolean("tbHints", false);
        engineOptions.hintsEdit = settings.getBoolean("tbHintsEdit", false);
        engineOptions.rootProbe = settings.getBoolean("tbRootProbe", true);
        engineOptions.engineProbe = settings.getBoolean("tbEngineProbe", true);

        String gtbPath = settings.getString("gtbPath", "").trim();
        if (gtbPath.length() == 0)
            gtbPath = StorageProvider.getGtbDefaultDir().getAbsolutePath();
        engineOptions.gtbPath = gtbPath;
        engineOptions.gtbPathNet = settings.getString("gtbPathNet", "").trim();
        String rtbPath = settings.getString("rtbPath", "").trim();
        if (rtbPath.length() == 0)
            rtbPath = StorageProvider.getRtbDefaultDir().getAbsolutePath();
        engineOptions.rtbPath = rtbPath;
        engineOptions.rtbPathNet = settings.getString("rtbPathNet", "").trim();
        engineOptions.workDir = StorageProvider.getEngineLogDir().getAbsolutePath();

        setEngineOptions(false);
        setEgtbHints(cb.getSelectedSquare());

        updateThinkingInfo();

        pgnOptions.view.variations  = settings.getBoolean("viewVariations",     true);
        pgnOptions.view.comments    = settings.getBoolean("viewComments",       true);
        pgnOptions.view.nag         = settings.getBoolean("viewNAG",            true);
        pgnOptions.view.headers     = settings.getBoolean("viewHeaders",        false);
        final int oldViewPieceType = pgnOptions.view.pieceType;
        pgnOptions.view.pieceType   = getIntSetting("viewPieceType", PGNOptions.PT_LOCAL);
        showVariationLine           = settings.getBoolean("showVariationLine",  false);
        pgnOptions.imp.variations   = settings.getBoolean("importVariations",   true);
        pgnOptions.imp.comments     = settings.getBoolean("importComments",     true);
        pgnOptions.imp.nag          = settings.getBoolean("importNAG",          true);
        pgnOptions.exp.variations   = settings.getBoolean("exportVariations",   true);
        pgnOptions.exp.comments     = settings.getBoolean("exportComments",     true);
        pgnOptions.exp.nag          = settings.getBoolean("exportNAG",          true);
        pgnOptions.exp.playerAction = settings.getBoolean("exportPlayerAction", false);
        pgnOptions.exp.clockInfo    = settings.getBoolean("exportTime",         false);

        ColorTheme.instance().readColors(settings);
        BoardTheme.instance().readSettings(settings);
        PieceSet.instance().readPrefs(settings);
        float brightness = settings.getFloat("boardBrightness", 1.0f);
        cb.setBoardBrightness(brightness);
        overrideViewAttribs();

        gameTextListener.clear();
        setPieceNames(pgnOptions.view.pieceType);
        ctrl.prefsChanged(oldViewPieceType != pgnOptions.view.pieceType);
        // update the typeset in case of a change anyway, cause it could occur
        // as well in rotation
        setFigurineNotation(pgnOptions.view.pieceType == PGNOptions.PT_FIGURINE, fontSize);

        boolean showMaterialDiff = settings.getBoolean("materialDiff", false);
        secondTitleLine.setVisibility(showMaterialDiff ? View.VISIBLE : View.GONE);
    }

    private void overrideViewAttribs() {
        Util.overrideViewAttribs(findViewById(R.id.main));
    }

    /**
     * Change the Pieces into figurine or regular (i.e. letters) display
     */
    private void setFigurineNotation(boolean displayAsFigures, int fontSize) {
        if (displayAsFigures) {
            // increase the font cause it has different kerning and looks small
            float increaseFontSize = fontSize * 1.1f;
            moveList.setTypeface(figNotation, increaseFontSize);
            thinking.setTypeface(figNotation);
            thinking.setTextSize(increaseFontSize);
        } else {
            moveList.setTypeface(null, fontSize);
            thinking.setTypeface(defaultThinkingListTypeFace);
            thinking.setTextSize(fontSize);
        }
    }

    /** Enable/disable title bar scrolling. */
    private void setTitleScrolling() {
        TextUtils.TruncateAt where = autoScrollTitle ? TextUtils.TruncateAt.MARQUEE
                                                     : TextUtils.TruncateAt.END;
        whiteTitleText.setEllipsize(where);
        blackTitleText.setEllipsize(where);
        whiteFigText.setEllipsize(where);
        blackFigText.setEllipsize(where);
    }

    private void updateButtons() {
        boolean largeButtons = settings.getBoolean("largeButtons", false);
        Resources r = getResources();
        int bWidth  = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, r.getDisplayMetrics()));
        int bHeight = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, r.getDisplayMetrics()));
        if (largeButtons) {
            if (custom1ButtonActions.isEnabled() &&
                custom2ButtonActions.isEnabled() &&
                custom3ButtonActions.isEnabled()) {
                Configuration config = getResources().getConfiguration();
                if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    bWidth  = bWidth  * 6 / 5;
                    bHeight = bHeight * 6 / 5;
                } else {
                    bWidth  = bWidth  * 5 / 4;
                    bHeight = bHeight * 5 / 4;
                }
            } else {
                bWidth  = bWidth  * 3 / 2;
                bHeight = bHeight * 3 / 2;
            }
        }
        SVG svg = null;
        try {
            svg = SVG.getFromResource(getResources(), R.raw.touch);
        } catch (SVGParseException ignore) {
        }
        setButtonData(custom1Button, bWidth, bHeight, custom1ButtonActions.getIcon(), svg);
        setButtonData(custom2Button, bWidth, bHeight, custom2ButtonActions.getIcon(), svg);
        setButtonData(custom3Button, bWidth, bHeight, custom3ButtonActions.getIcon(), svg);
        setButtonData(modeButton, bWidth, bHeight, R.raw.mode, svg);
        setButtonData(undoButton, bWidth, bHeight, R.raw.left, svg);
        setButtonData(redoButton, bWidth, bHeight, R.raw.right, svg);
    }

    @SuppressWarnings("deprecation")
    private void setButtonData(ImageButton button, int bWidth, int bHeight,
                                     int svgResId, SVG touched) {
        SVG svg = null;
        try {
            svg = SVG.getFromResource(getResources(), svgResId);
        } catch (SVGParseException ignore) {
        }
        button.setBackgroundDrawable(new SVGPictureDrawable(svg));

        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, new SVGPictureDrawable(touched));
        button.setImageDrawable(sld);

        LayoutParams lp = button.getLayoutParams();
        lp.height = bHeight;
        lp.width = bWidth;
        button.setLayoutParams(lp);
        button.setPadding(0,0,0,0);
        button.setScaleType(ScaleType.FIT_XY);
    }

    @SuppressLint("Wakelock")
    private synchronized void setWakeLock(boolean enableLock) {
        if (enableLock)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void setEngine(String engine) {
        if (!storageAvailable()) {
            if (!"stockfish".equals(engine) && !"cuckoochess".equals(engine))
                engine = "stockfish";
        }
        ctrl.setEngine(engine);
        setEngineTitle(engine, ctrl.eloData().getEloToUse());
    }

    private void setEngineTitle(String engine, int elo) {
        String eName = "";
        if (EngineUtil.isOpenExchangeEngine(engine)) {
            String engineFileName = new File(engine).getName();
            ChessEngineResolver resolver = new ChessEngineResolver(this);
            List<ChessEngine> engines = resolver.resolveEngines();
            for (ChessEngine ce : engines) {
                if (EngineUtil.openExchangeFileName(ce).equals(engineFileName)) {
                    eName = ce.getName();
                    break;
                }
            }
        } else if (engine.contains("/")) {
            int idx = engine.lastIndexOf('/');
            eName = engine.substring(idx + 1);
        } else {
            eName = getString("cuckoochess".equals(engine) ?
                              R.string.cuckoochess_engine :
                              R.string.stockfish_engine);
        }
        if (ctrl != null && !ctrl.analysisMode())
            if (elo != Integer.MAX_VALUE)
                eName = String.format(Locale.US, "%s: %d", eName, elo);
        engineTitleText.setText(eName);
    }

    /** Update center field in second header line. */
    public final void updateTimeControlTitle() {
        int[] tmpInfo = ctrl.getTimeLimit();
        StringBuilder sb = new StringBuilder();
        int tc = tmpInfo[0];
        int mps = tmpInfo[1];
        int inc = tmpInfo[2];
        if (mps > 0) {
            sb.append(mps);
            sb.append("/");
        }
        sb.append(timeToString(tc));
        if ((inc > 0) || (mps <= 0)) {
            sb.append("+");
            sb.append(tmpInfo[2] / 1000);
        }
        summaryTitleText.setText(sb.toString());
    }

    @Override
    public void updateEngineTitle(int elo) {
        String engine = settings.getString("engine", "stockfish");
        setEngineTitle(engine, elo);
    }

    @Override
    public void updateMaterialDifferenceTitle(Util.MaterialDiff diff) {
        whiteFigText.setText(diff.white);
        blackFigText.setText(diff.black);
    }

    private void setBookOptions() {
        BookOptions options = new BookOptions(bookOptions);
        if (options.filename.isEmpty())
            options.filename = "internal:";
        if (!options.filename.endsWith(":")) {
            String sep = File.separator;
            if (!options.filename.startsWith(sep)) {
                options.filename = StorageProvider.getBookDir().getAbsolutePath() + sep + options.filename;
            }
        }
        ctrl.setBookOptions(options);
    }

    private boolean egtbForceReload = false;

    private void setEngineOptions(boolean restart) {
        computeNetEngineID();
        ctrl.setEngineOptions(new EngineOptions(engineOptions), restart);
        Probe.getInstance().setPath(engineOptions.gtbPath, engineOptions.rtbPath,
                                    egtbForceReload);
        egtbForceReload = false;
    }

    private void computeNetEngineID() {
        String id = "";
        try {
            String engine = settings.getString("engine", "stockfish");
            if (EngineUtil.isNetEngine(engine)) {
                String[] lines = FileUtil.readFile(engine);
                if (lines.length >= 3)
                    id = lines[1] + ":" + lines[2];
            }
        } catch (IOException ignore) {
        }
        engineOptions.networkID = id;
    }

    void setEgtbHints(int sq) {
        if (!engineOptions.hints || (sq < 0)) {
            cb.setSquareDecorations(null);
            return;
        }

        Probe probe = Probe.getInstance();
        ArrayList<Pair<Integer,ProbeResult>> x = probe.movePieceProbe(cb.pos, sq);
        if (x == null) {
            cb.setSquareDecorations(null);
            return;
        }

        ArrayList<SquareDecoration> sd = new ArrayList<>();
        for (Pair<Integer,ProbeResult> p : x)
            sd.add(new SquareDecoration(p.first, p.second));
        cb.setSquareDecorations(sd);
    }

    private class DrawerItem {
        DrawerItemId id;
        private int resId; // Item string resource id

        DrawerItem(DrawerItemId id, int resId) {
            this.id = id;
            this.resId = resId;
        }

        @Override
        public String toString() {
            return getString(resId);
        }
    }

    private enum DrawerItemId {
        NEW_GAME,
        ENGINE_MATCH,
        SET_STRENGTH,
        EDIT_BOARD,
        SETTINGS,
        FILE_MENU,
        RESIGN,
        FORCE_MOVE,
        DRAW,
        SELECT_BOOK,
        MANAGE_ENGINES,
        SET_COLOR_THEME,
        SET_BOARD_TEXTURE,
        ABOUT,
    }

    /** Initialize the drawer part of the user interface. */
    private void initDrawers() {
        drawerLayout = findViewById(R.id.drawer_layout);
        leftDrawer = findViewById(R.id.left_drawer);
        rightDrawer = findViewById(R.id.right_drawer);

        final DrawerItem[] leftItems = new DrawerItem[] {
            new DrawerItem(DrawerItemId.NEW_GAME, R.string.option_new_game),
            new DrawerItem(DrawerItemId.ENGINE_MATCH, R.string.option_engine_match),
            new DrawerItem(DrawerItemId.SET_STRENGTH, R.string.set_engine_strength),
            new DrawerItem(DrawerItemId.EDIT_BOARD, R.string.option_edit_board),
            new DrawerItem(DrawerItemId.FILE_MENU, R.string.option_file),
            new DrawerItem(DrawerItemId.SELECT_BOOK, R.string.option_select_book),
            new DrawerItem(DrawerItemId.MANAGE_ENGINES, R.string.option_manage_engines),
            new DrawerItem(DrawerItemId.SET_COLOR_THEME, R.string.option_color_theme),
            new DrawerItem(DrawerItemId.SET_BOARD_TEXTURE, R.string.option_board_texture),
            new DrawerItem(DrawerItemId.SETTINGS, R.string.option_settings),
            new DrawerItem(DrawerItemId.ABOUT, R.string.option_about),
        };
        leftDrawer.setAdapter(new ArrayAdapter<>(this,
                                                 R.layout.drawer_list_item,
                                                 leftItems));
        leftDrawer.setOnItemClickListener((parent, view, position, id) -> {
            DrawerItem di = leftItems[position];
            handleDrawerSelection(di.id);
        });

        final DrawerItem[] rightItems = new DrawerItem[] {
            new DrawerItem(DrawerItemId.RESIGN, R.string.option_resign_game),
            new DrawerItem(DrawerItemId.FORCE_MOVE, R.string.option_force_computer_move),
            new DrawerItem(DrawerItemId.DRAW, R.string.option_draw),
        };
        rightDrawer.setAdapter(new ArrayAdapter<>(this,
                                                  R.layout.drawer_list_item,
                                                  rightItems));
        rightDrawer.setOnItemClickListener((parent, view, position, id) -> {
            DrawerItem di = rightItems[position];
            handleDrawerSelection(di.id);
        });
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        drawerLayout.openDrawer(Gravity.LEFT);
        return false;
    }

    /** React to a selection in the left/right drawers. */
    private void handleDrawerSelection(DrawerItemId id) {
        drawerLayout.closeDrawer(Gravity.LEFT);
        drawerLayout.closeDrawer(Gravity.RIGHT);
        leftDrawer.clearChoices();
        rightDrawer.clearChoices();

        setAutoMode(AutoMode.OFF);

        switch (id) {
        case NEW_GAME:
            showDroidFishDialog(NEW_GAME_DIALOG);
            break;
        case ENGINE_MATCH:
            showDroidFishDialog(ENGINE_MATCH_DIALOG);
            break;
        case SET_STRENGTH:
            reShowDialog(SET_STRENGTH_DIALOG);
            break;
        case EDIT_BOARD:
            startEditBoard(ctrl.getFEN());
            break;
        case SETTINGS: {
            Intent i = new Intent(DroidFish.this, Preferences.class);
            startActivityForResult(i, RESULT_SETTINGS);
            break;
        }
        case FILE_MENU:
            if (storageAvailable())
                reShowDialog(FILE_MENU_DIALOG);
            break;
        case RESIGN:
            if (ctrl.humansTurn())
                ctrl.resignGame();
            break;
        case FORCE_MOVE:
            ctrl.stopSearch();
            break;
        case DRAW:
            if (ctrl.humansTurn()) {
                if (ctrl.claimDrawIfPossible())
                    ctrl.stopPonder();
                else
                    DroidFishApp.toast(R.string.offer_draw, Toast.LENGTH_SHORT);
            }
            break;
        case SELECT_BOOK:
            if (storageAvailable())
                reShowDialog(SELECT_BOOK_DIALOG);
            break;
        case MANAGE_ENGINES:
            if (storageAvailable())
                reShowDialog(MANAGE_ENGINES_DIALOG);
            else
                reShowDialog(SELECT_ENGINE_DIALOG_NOMANAGE);
            break;
        case SET_COLOR_THEME:
            showDroidFishDialog(SET_COLOR_THEME_DIALOG);
            break;
        case SET_BOARD_TEXTURE:
            showDroidFishDialog(SET_BOARD_TEXTURE_DIALOG);
            break;
        case ABOUT:
            showDroidFishDialog(ABOUT_DIALOG);
            break;
        }
    }

    static private final int RESULT_EDITBOARD   =  0;
    static private final int RESULT_SETTINGS    =  1;
    static private final int RESULT_LOAD_PGN    =  2;
    static private final int RESULT_LOAD_FEN    =  3;
    static private final int RESULT_SAVE_PGN    =  4;
    static private final int RESULT_SELECT_SCID =  5;
    static private final int RESULT_OI_PGN_SAVE =  6;
    static private final int RESULT_OI_PGN_LOAD =  7;
    static private final int RESULT_OI_FEN_LOAD =  8;
    static private final int RESULT_GET_FEN     =  9;
    static private final int RESULT_EDITOPTIONS = 10;

    private ActivityResultLauncher<String[]> openPgnLauncher;
    private ActivityResultLauncher<String[]> openFenLauncher;
    private ActivityResultLauncher<String> createPgnLauncher;

    private void registerSAFLaunchers() {
        openPgnLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    String path = copyUriToLocal(uri, StorageProvider.getPgnDir(), ".saf_import.pgn");
                    if (path != null)
                        loadPGNFromFile(path);
                }
            });
        openFenLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    String path = copyUriToLocal(uri, StorageProvider.getFenDir(), ".saf_import.epd");
                    if (path != null)
                        loadFENFromFile(path);
                }
            });
        createPgnLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/x-chess-pgn"),
            uri -> {
                if (uri != null) {
                    savePGNToUri(uri);
                }
            });
    }

    private String copyUriToLocal(Uri uri, File targetDir, String localName) {
        try {
            File localFile = new File(targetDir, localName);
            try (InputStream is = getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(localFile)) {
                if (is == null) return null;
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) > 0)
                    fos.write(buf, 0, len);
            }
            return localFile.getAbsolutePath();
        } catch (IOException e) {
            return null;
        }
    }

    private void savePGNToUri(Uri uri) {
        String pgn = ctrl.getPGN();
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os != null)
                os.write(pgn.getBytes("UTF-8"));
        } catch (IOException e) {
            DroidFishApp.toast(R.string.failed_to_save_game, Toast.LENGTH_SHORT);
        }
    }

    private void startEditBoard(String fen) {
        Intent i = new Intent(DroidFish.this, EditBoard.class);
        i.setAction(fen);
        startActivityForResult(i, RESULT_EDITBOARD);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case RESULT_SETTINGS:
            handlePrefsChange();
            break;
        case RESULT_EDITBOARD:
            if (resultCode == RESULT_OK) {
                try {
                    String fen = data.getAction();
                    ctrl.setFENOrPGN(fen, true);
                    setBoardFlip(false);
                } catch (ChessParseError ignore) {
                }
            }
            break;
        case RESULT_LOAD_PGN:
            if (resultCode == RESULT_OK) {
                try {
                    String pgnToken = data.getAction();
                    String pgn = cache.retrieveString(pgnToken);
                    int modeNr = ctrl.getGameMode().getModeNr();
                    if ((modeNr != GameMode.ANALYSIS) && (modeNr != GameMode.EDIT_GAME))
                        newGameMode(GameMode.EDIT_GAME);
                    ctrl.setFENOrPGN(pgn, false);
                    setBoardFlip(true);
                } catch (ChessParseError e) {
                    DroidFishApp.toast(getParseErrString(e), Toast.LENGTH_SHORT);
                }
            }
            break;
        case RESULT_SAVE_PGN:
            if (resultCode == RESULT_OK) {
                long hash = data.getLongExtra("org.petero.droidfish.treeHash", -1);
                ctrl.setLastSaveHash(hash);
            }
            break;
        case RESULT_SELECT_SCID:
            if (resultCode == RESULT_OK) {
                String pathName = data.getAction();
                if (pathName != null) {
                    Editor editor = settings.edit();
                    editor.putString("currentScidFile", pathName);
                    editor.putInt("currFT", FT_SCID);
                    editor.apply();
                    Intent i = new Intent(DroidFish.this, LoadScid.class);
                    i.setAction("org.petero.droidfish.loadScid");
                    i.putExtra("org.petero.droidfish.pathname", pathName);
                    startActivityForResult(i, RESULT_LOAD_PGN);
                }
            }
            break;
        case RESULT_OI_PGN_LOAD:
            if (resultCode == RESULT_OK) {
                String pathName = FileUtil.getFilePathFromUri(data.getData());
                if (pathName != null)
                    loadPGNFromFile(pathName);
            }
            break;
        case RESULT_OI_PGN_SAVE:
            if (resultCode == RESULT_OK) {
                String pathName = FileUtil.getFilePathFromUri(data.getData());
                if (pathName != null) {
                    if ((pathName.length() > 0) && !pathName.contains("."))
                        pathName += ".pgn";
                    savePGNToFile(pathName);
                }
            }
            break;
        case RESULT_OI_FEN_LOAD:
            if (resultCode == RESULT_OK) {
                String pathName = FileUtil.getFilePathFromUri(data.getData());
                if (pathName != null)
                    loadFENFromFile(pathName);
            }
            break;
        case RESULT_GET_FEN:
            if (resultCode == RESULT_OK) {
                String fen = data.getStringExtra(Intent.EXTRA_TEXT);
                if (fen == null) {
                    String pathName = FileUtil.getFilePathFromUri(data.getData());
                    loadFENFromFile(pathName);
                }
                setFenHelper(fen, true);
            }
            break;
        case RESULT_LOAD_FEN:
            if (resultCode == RESULT_OK) {
                String fen = data.getAction();
                setFenHelper(fen, false);
            }
            break;
        case RESULT_EDITOPTIONS:
            if (resultCode == RESULT_OK) {
                @SuppressWarnings("unchecked")
                Map<String,String> uciOpts =
                    (Map<String,String>)data.getSerializableExtra("org.petero.droidfish.ucioptions");
                ctrl.setEngineUCIOptions(uciOpts);
            }
            break;
        }
    }

    /** Set new game mode. */
    private void newGameMode(int gameModeType) {
        Editor editor = settings.edit();
        String gameModeStr = String.format(Locale.US, "%d", gameModeType);
        editor.putString("gameMode", gameModeStr);
        editor.apply();
        gameMode = new GameMode(gameModeType);
        maybeAutoModeOff(gameMode);
        ctrl.setGameMode(gameMode);
        updateEvalBarVisibility();
    }

    /**
     * Update eval bar visibility based on the current game mode and preferences.
     * - Analysis mode: controlled by evalBarAnalysis preference ("off", "barOnly", "textOnly", "both")
     * - Human vs Engine (PLAYER_WHITE or PLAYER_BLACK): controlled by evalBarHvE preference
     * - All other modes: eval bar is hidden
     */
    private void updateEvalBarVisibility() {
        if (evalBar == null)
            return;
        int modeNr = gameMode.getModeNr();
        boolean showBar = false;
        if (modeNr == GameMode.ANALYSIS) {
            showBar = "barOnly".equals(mEvalBarAnalysis) || "both".equals(mEvalBarAnalysis);
        } else if (modeNr == GameMode.PLAYER_WHITE || modeNr == GameMode.PLAYER_BLACK) {
            showBar = mEvalBarHvE;
        }
        evalBar.setVisibility(showBar ? View.VISIBLE : View.GONE);
        if (!showBar) {
            evalBar.resetToNeutral();
        }
    }

    private String getParseErrString(ChessParseError e) {
        if (e.resourceId == -1)
            return e.getMessage();
        else
            return getString(e.resourceId);
    }

    private int nameMatchScore(String name, String match) {
        if (name == null)
            return 0;
        String lName = name.toLowerCase(Locale.US);
        String lMatch = match.toLowerCase(Locale.US);
        if (name.equals(match))
            return 6;
        if (lName.equals(lMatch))
            return 5;
        if (name.startsWith(match))
            return 4;
        if (lName.startsWith(lMatch))
            return 3;
        if (name.contains(match))
            return 2;
        if (lName.contains(lMatch))
            return 1;
        return 0;
    }

    private void setBoardFlip() {
        setBoardFlip(false);
    }

    /** Set a boolean preference setting. */
    private void setBooleanPref(String name, boolean value) {
        Editor editor = settings.edit();
        editor.putBoolean(name, value);
        editor.apply();
    }

    /** Toggle a boolean preference setting. Return new value. */
    private boolean toggleBooleanPref(String name) {
        boolean value = !settings.getBoolean(name, false);
        setBooleanPref(name, value);
        return value;
    }

    private void setBoardFlip(boolean matchPlayerNames) {
        boolean flipped = boardFlipped;
        if (playerNameFlip && matchPlayerNames && (ctrl != null)) {
            final TreeMap<String,String> headers = new TreeMap<>();
            ctrl.getHeaders(headers);
            int whiteMatch = nameMatchScore(headers.get("White"), playerName);
            int blackMatch = nameMatchScore(headers.get("Black"), playerName);
            if (( flipped && (whiteMatch > blackMatch)) ||
                (!flipped && (whiteMatch < blackMatch))) {
                flipped = !flipped;
                boardFlipped = flipped;
                setBooleanPref("boardFlipped", flipped);
            }
        }
        if (autoSwapSides) {
            if (gameMode.analysisMode()) {
                flipped = !cb.pos.whiteMove;
            } else if (gameMode.playerWhite() && gameMode.playerBlack()) {
                flipped = !cb.pos.whiteMove;
            } else if (gameMode.playerWhite()) {
                flipped = false;
            } else if (gameMode.playerBlack()) {
                flipped = true;
            } else { // two computers
                flipped = !cb.pos.whiteMove;
            }
        }
        cb.setFlipped(flipped);
    }

    @Override
    public void setSelection(int sq) {
        cb.setSelection(cb.highlightLastMove ? sq : -1);
        cb.userSelectedSquare = false;
        setEgtbHints(sq);
    }

    @Override
    public void setStatus(GameStatus s) {
        String str;
        switch (s.state) {
        case ALIVE:
            str = Integer.valueOf(s.moveNr).toString();
            if (s.white)
                str += ". " + getString(R.string.whites_move);
            else
                str += "... " + getString(R.string.blacks_move);
            if (s.ponder) str += " (" + getString(R.string.ponder) + ")";
            if (s.thinking) str += " (" + getString(R.string.thinking) + ")";
            if (s.analyzing) str += " (" + getString(R.string.analyzing) + ")";
            break;
        case WHITE_MATE:
            str = getString(R.string.white_mate);
            break;
        case BLACK_MATE:
            str = getString(R.string.black_mate);
            break;
        case WHITE_STALEMATE:
        case BLACK_STALEMATE:
            str = getString(R.string.stalemate);
            break;
        case DRAW_REP: {
            str = getString(R.string.draw_rep);
            if (s.drawInfo.length() > 0)
                str = str + " [" + s.drawInfo + "]";
            break;
        }
        case DRAW_50: {
            str = getString(R.string.draw_50);
            if (s.drawInfo.length() > 0)
                str = str + " [" + s.drawInfo + "]";
            break;
        }
        case DRAW_NO_MATE:
            str = getString(R.string.draw_no_mate);
            break;
        case DRAW_AGREE:
            str = getString(R.string.draw_agree);
            break;
        case RESIGN_WHITE:
            str = getString(R.string.resign_white);
            break;
        case RESIGN_BLACK:
            str = getString(R.string.resign_black);
            break;
        default:
            throw new RuntimeException();
        }
        setStatusString(str);
    }

    private void setStatusString(String str) {
        status.setText(str);
    }

    @Override
    public void moveListUpdated() {
        moveList.setText(gameTextListener.getText());
        int currPos = gameTextListener.getCurrPos();
        int line = moveList.getLineForOffset(currPos);
        if (line >= 0 && autoScrollMoveList) {
            int y = moveList.getLineStartY(line - 1);
            moveListScroll.scrollTo(0, y);
        }
    }

    @Override
    public boolean whiteBasedScores() {
        return mWhiteBasedScores;
    }

    @Override
    public boolean ponderMode() {
        return mPonderMode;
    }

    @Override
    public String playerName() {
        return playerName;
    }

    @Override
    public boolean discardVariations() {
        return discardVariations;
    }

    /** Report a move made that is a candidate for GUI animation. */
    public void setAnimMove(Position sourcePos, Move move, boolean forward) {
        if (animateMoves && (move != null))
            cb.setAnimMove(sourcePos, move, forward);
    }

    @Override
    public void setPosition(Position pos, String variantInfo, ArrayList<Move> variantMoves) {
        variantStr = variantInfo;
        this.variantMoves = variantMoves;
        cb.setPosition(pos);
        setBoardFlip();
        updateThinkingInfo();
        setEgtbHints(cb.getSelectedSquare());
    }

    private String thinkingStr1 = "";
    private String thinkingStr2 = "";
    private String bookInfoStr = "";
    private String ecoInfoStr = "";
    private int distToEcoTree = 0;
    private String variantStr = "";
    private ArrayList<ArrayList<Move>> pvMoves = new ArrayList<>();
    private ArrayList<Move> bookMoves = null;
    private ArrayList<Move> variantMoves = null;

    @Override
    public void setThinkingInfo(ThinkingInfo ti) {
        thinkingStr1 = ti.pvStr;
        thinkingStr2 = ti.statStr;
        bookInfoStr = ti.bookInfo;
        ecoInfoStr = ti.eco;
        distToEcoTree = ti.distToEcoTree;
        pvMoves = ti.pvMoves;
        bookMoves = ti.bookMoves;
        if (evalBar != null && evalBar.getVisibility() == View.VISIBLE) {
            evalBar.setScore(ti.evalScore, ti.evalIsMate);
        }
        updateThinkingInfo();

        if (ctrl.computerBusy()) {
            lastComputationMillis = System.currentTimeMillis();
        } else {
            lastComputationMillis = 0;
        }
        updateNotification();
    }

    /** Truncate line to max "maxLen" characters. Truncates at
     *  space character if possible. */
    private String truncateLine(String line, int maxLen) {
        if (line.length() <= maxLen || maxLen <= 0)
            return line;
        int idx = line.lastIndexOf(' ', maxLen-1);
        if (idx > 0)
            return line.substring(0, idx);
        return line.substring(0, maxLen);
    }

    private void updateThinkingInfo() {
        boolean thinkingEmpty = true;
        {
            StringBuilder sb = new StringBuilder(128);
            // In analysis mode, suppress thinking text when evalBarAnalysis is "barOnly"
            boolean showThinkingText = mShowThinking || gameMode.analysisMode();
            if (gameMode.analysisMode() && "barOnly".equals(mEvalBarAnalysis)) {
                showThinkingText = false;
            }
            if (showThinkingText) {
                if (!thinkingStr1.isEmpty()) {
                    if (fullPVLines) {
                        sb.append(thinkingStr1);
                    } else {
                        String[] lines = thinkingStr1.split("\n");
                        int w = thinking.getWidth();
                        for (int i = 0; i < lines.length; i++) {
                            String line = lines[i];
                            if (i > 0)
                                sb.append('\n');
                            int n = thinking.getPaint().breakText(line, true, w, null);
                            sb.append(truncateLine(lines[i], n));
                        }
                    }
                    thinkingEmpty = false;
                }
                if (mShowStats) {
                    if (!thinkingEmpty)
                        sb.append('\n');
                    sb.append(thinkingStr2);
                    if (!thinkingStr2.isEmpty()) thinkingEmpty = false;
                }
            }
            thinking.setText(sb.toString(), TextView.BufferType.SPANNABLE);
        }
        int maxDistToEcoTree = 10;
        if ((mEcoHints == ECO_HINTS_ALWAYS ||
            (mEcoHints == ECO_HINTS_AUTO && distToEcoTree <= maxDistToEcoTree)) &&
            !ecoInfoStr.isEmpty()) {
            String s = thinkingEmpty ? "" : "<br>";
            s += ecoInfoStr;
            thinking.append(Html.fromHtml(s));
            thinkingEmpty = false;
        }
        if (mShowBookHints && !bookInfoStr.isEmpty() && ctrl.humansTurn()) {
            String s = thinkingEmpty ? "" : "<br>";
            s += Util.boldStart + getString(R.string.book) + Util.boldStop + bookInfoStr;
            thinking.append(Html.fromHtml(s));
            thinkingEmpty = false;
        }
        if (showVariationLine && (variantStr.indexOf(' ') >= 0)) {
            String s = thinkingEmpty ? "" : "<br>";
            s += Util.boldStart + getString(R.string.variation) + Util.boldStop + variantStr;
            thinking.append(Html.fromHtml(s));
            thinkingEmpty = false;
        }
        thinking.setVisibility(thinkingEmpty ? View.GONE : View.VISIBLE);

        List<Move> hints = null;
        if (mShowThinking || gameMode.analysisMode()) {
            ArrayList<ArrayList<Move>> pvMovesTmp = pvMoves;
            if (pvMovesTmp.size() == 1) {
                hints = pvMovesTmp.get(0);
            } else if (pvMovesTmp.size() > 1) {
                hints = new ArrayList<>();
                for (ArrayList<Move> pv : pvMovesTmp)
                    if (!pv.isEmpty())
                        hints.add(pv.get(0));
            }
        }
        if ((hints == null) && mShowBookHints)
            hints = bookMoves;
        if (((hints == null) || hints.isEmpty()) &&
            (variantMoves != null) && variantMoves.size() > 1) {
            hints = variantMoves;
        }
        if ((hints != null) && (hints.size() > maxNumArrows)) {
            hints = hints.subList(0, maxNumArrows);
        }
        cb.setMoveHints(hints);
    }

    static private final int PROMOTE_DIALOG = 0;
    static         final int BOARD_MENU_DIALOG = 1;
    static private final int ABOUT_DIALOG = 2;
    static private final int SELECT_BOOK_DIALOG = 4;
    static private final int SELECT_ENGINE_DIALOG = 5;
    static private final int SELECT_ENGINE_DIALOG_NOMANAGE = 6;
    static private final int SELECT_PGN_FILE_DIALOG = 7;
    static private final int SELECT_PGN_FILE_SAVE_DIALOG = 8;
    static private final int SET_COLOR_THEME_DIALOG = 9;
    static private final int GAME_MODE_DIALOG = 10;
    static private final int SELECT_PGN_SAVE_NEWFILE_DIALOG = 11;
    static private final int MOVELIST_MENU_DIALOG = 12;
    static private final int THINKING_MENU_DIALOG = 13;
    static private final int GO_BACK_MENU_DIALOG = 14;
    static private final int GO_FORWARD_MENU_DIALOG = 15;
    static private final int FILE_MENU_DIALOG = 16;
    static private final int NEW_GAME_DIALOG = 17;
    static private final int CUSTOM1_BUTTON_DIALOG = 18;
    static private final int CUSTOM2_BUTTON_DIALOG = 19;
    static private final int CUSTOM3_BUTTON_DIALOG = 20;
    static private final int MANAGE_ENGINES_DIALOG = 21;
    static private final int NETWORK_ENGINE_DIALOG = 22;
    static private final int NEW_NETWORK_ENGINE_DIALOG = 23;
    static private final int NETWORK_ENGINE_CONFIG_DIALOG = 24;
    static private final int DELETE_NETWORK_ENGINE_DIALOG = 25;
    static private final int CLIPBOARD_DIALOG = 26;
    static private final int SELECT_FEN_FILE_DIALOG = 27;
    static private final int SET_STRENGTH_DIALOG = 28;
    static private final int SET_BOARD_TEXTURE_DIALOG = 29;
    static private final int ENGINE_MATCH_DIALOG = 30;

    /** Show a dialog, dismissing any existing instance with the same tag. */
    void reShowDialog(int id) {
        showDroidFishDialog(id);
    }

    private void showDroidFishDialog(int id) {
        String tag = "df_dialog_" + id;
        androidx.fragment.app.Fragment existing = getSupportFragmentManager().findFragmentByTag(tag);
        if (existing instanceof DialogFragment)
            ((DialogFragment) existing).dismissAllowingStateLoss();
        DroidFishDialogFragment.newInstance(id).show(getSupportFragmentManager(), tag);
    }

    @Override
    public Dialog createDialogById(int id) {
        switch (id) {
        case NEW_GAME_DIALOG:                return newGameDialog();
        case SET_STRENGTH_DIALOG:            return setStrengthDialog();
        case PROMOTE_DIALOG:                 return promoteDialog();
        case BOARD_MENU_DIALOG:              return boardMenuDialog();
        case FILE_MENU_DIALOG:               return fileMenuDialog();
        case ABOUT_DIALOG:                   return aboutDialog();
        case SELECT_BOOK_DIALOG:             return selectBookDialog();
        case SELECT_ENGINE_DIALOG:           return selectEngineDialog(false);
        case SELECT_ENGINE_DIALOG_NOMANAGE:  return selectEngineDialog(true);
        case SELECT_PGN_FILE_DIALOG:         return selectPgnFileDialog();
        case SELECT_PGN_FILE_SAVE_DIALOG:    return selectPgnFileSaveDialog();
        case SELECT_PGN_SAVE_NEWFILE_DIALOG: return selectPgnSaveNewFileDialog();
        case SET_COLOR_THEME_DIALOG:         return setColorThemeDialog();
        case SET_BOARD_TEXTURE_DIALOG:       return setBoardTextureDialog();
        case GAME_MODE_DIALOG:               return gameModeDialog();
        case MOVELIST_MENU_DIALOG:           return moveListMenuDialog();
        case THINKING_MENU_DIALOG:           return thinkingMenuDialog();
        case GO_BACK_MENU_DIALOG:            return goBackMenuDialog();
        case GO_FORWARD_MENU_DIALOG:         return goForwardMenuDialog();
        case CUSTOM1_BUTTON_DIALOG:          return makeButtonDialog(custom1ButtonActions);
        case CUSTOM2_BUTTON_DIALOG:          return makeButtonDialog(custom2ButtonActions);
        case CUSTOM3_BUTTON_DIALOG:          return makeButtonDialog(custom3ButtonActions);
        case MANAGE_ENGINES_DIALOG:          return manageEnginesDialog();
        case NETWORK_ENGINE_DIALOG:          return networkEngineDialog();
        case NEW_NETWORK_ENGINE_DIALOG:      return newNetworkEngineDialog();
        case NETWORK_ENGINE_CONFIG_DIALOG:   return networkEngineConfigDialog();
        case DELETE_NETWORK_ENGINE_DIALOG:   return deleteNetworkEngineDialog();
        case CLIPBOARD_DIALOG:               return clipBoardDialog();
        case SELECT_FEN_FILE_DIALOG:         return selectFenFileDialog();
        case ENGINE_MATCH_DIALOG:            return engineMatchDialog();
        }
        return null;
    }

    private Dialog newGameDialog() {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.option_new_game);
        builder.setMessage(R.string.start_new_game);
        builder.setNeutralButton(R.string.yes, (dialog, which) -> startNewGame(2));
        builder.setNegativeButton(R.string.white, (dialog, which) -> startNewGame(0));
        builder.setPositiveButton(R.string.black, (dialog, which) -> startNewGame(1));
        return builder.create();
    }

    private Dialog setStrengthDialog() {
        EloStrengthSetter m = new EloStrengthSetter();
        return m.getDialog();
    }

    /** Handle user interface to set engine strength. */
    private class EloStrengthSetter {
        private final EloData eloData = ctrl.eloData();

        private CheckBox checkBox;
        private TextView eloLabel;
        private EditText editTxt;
        private SeekBar seekBar;

        private int progressToElo(int p) {
            return eloData.minElo + p;
        }

        private int eloToProgress(int elo) {
            return elo - eloData.minElo;
        }

        private void updateText(int elo) {
            String txt = Integer.valueOf(elo).toString();
            if (!txt.equals(editTxt.getText().toString())) {
                editTxt.setText(txt);
                editTxt.setSelection(txt.length());
            }
        }

        private void updateEnabledState(boolean enabled) {
            eloLabel.setEnabled(enabled);
            editTxt.setEnabled(enabled);
            seekBar.setEnabled(enabled);
        }

        public Dialog getDialog() {
            if (!eloData.canChangeStrength()) {
                DroidFishApp.toast(R.string.engine_cannot_reduce_strength, Toast.LENGTH_LONG);
                return null;
            }
            AlertDialog.Builder builder = new MaterialAlertDialogBuilder(DroidFish.this);
            builder.setTitle(R.string.set_engine_strength);
            View content = View.inflate(DroidFish.this, R.layout.set_strength, null);
            builder.setView(content);

            checkBox = content.findViewById(R.id.strength_checkbox);
            eloLabel = content.findViewById(R.id.strength_elolabel);
            editTxt = content.findViewById(R.id.strength_edittext);
            seekBar = content.findViewById(R.id.strength_seekbar);

            checkBox.setChecked(eloData.limitStrength);
            seekBar.setMax(eloToProgress(eloData.maxElo));
            seekBar.setProgress(eloToProgress(eloData.elo));
            updateText(eloData.elo);
            updateEnabledState(eloData.limitStrength);

            checkBox.setOnCheckedChangeListener((button, isChecked) -> {
                updateEnabledState(isChecked);
            });
            seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) { }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateText(progressToElo(progress));
                }
            });
            editTxt.addTextChangedListener(new TextWatcher() {
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String txt = editTxt.getText().toString();
                    try {
                        int elo = Integer.parseInt(txt);
                        int p = eloToProgress(elo);
                        if (p != seekBar.getProgress())
                            seekBar.setProgress(p);
                        updateText(progressToElo(p));
                    } catch (NumberFormatException ignore) {
                    }
                }
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override
                public void afterTextChanged(Editable s) { }
            });

            builder.setNegativeButton(R.string.cancel, null);
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                boolean limitStrength = checkBox.isChecked();
                int elo = progressToElo(seekBar.getProgress());
                ctrl.setStrength(limitStrength, elo);
            });

            return builder.create();
        }
    }

    private void startNewGame(int type) {
        if (type != 2) {
            int gameModeType = (type == 0) ? GameMode.PLAYER_WHITE : GameMode.PLAYER_BLACK;
            Editor editor = settings.edit();
            String gameModeStr = String.format(Locale.US, "%d", gameModeType);
            editor.putString("gameMode", gameModeStr);
            editor.apply();
            gameMode = new GameMode(gameModeType);
        }
        TimeControlData tcData = new TimeControlData();
        tcData.setTimeControl(timeControl, movesPerSession, timeIncrement);
        speech.flushQueue();
        ctrl.newGame(gameMode, tcData);
        ctrl.startGame();
        setBoardFlip(true);
        updateEngineTitle(ctrl.eloData().getEloToUse()); // Game mode affects Elo setting
    }

    private Dialog promoteDialog() {
        final String[] items = {
            getString(R.string.queen), getString(R.string.rook),
            getString(R.string.bishop), getString(R.string.knight)
        };
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.promote_pawn_to);
        builder.setItems(items, (dialog, item) -> ctrl.reportPromotePiece(item));
        return builder.create();
    }

    private Dialog clipBoardDialog() {
        final int COPY_GAME      = 0;
        final int COPY_POSITION  = 1;
        final int PASTE          = 2;

        setAutoMode(AutoMode.OFF);
        List<String> lst = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.copy_game));     actions.add(COPY_GAME);
        lst.add(getString(R.string.copy_position)); actions.add(COPY_POSITION);
        lst.add(getString(R.string.paste));         actions.add(PASTE);
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.tools_menu);
        builder.setItems(lst.toArray(new String[0]), (dialog, item) -> {
            switch (actions.get(item)) {
            case COPY_GAME: {
                String pgn = ctrl.getPGN();
                ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(new ClipData("DroidFish game",
                        new String[]{ "application/x-chess-pgn", ClipDescription.MIMETYPE_TEXT_PLAIN },
                        new ClipData.Item(pgn)));
                break;
            }
            case COPY_POSITION: {
                String fen = ctrl.getFEN() + "\n";
                ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(new ClipData(fen,
                        new String[]{ "application/x-chess-fen", ClipDescription.MIMETYPE_TEXT_PLAIN },
                        new ClipData.Item(fen)));
                break;
            }
            case PASTE: {
                ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null) {
                    StringBuilder fenPgn = new StringBuilder();
                    for (int i = 0; i < clip.getItemCount(); i++)
                        fenPgn.append(clip.getItemAt(i).coerceToText(getApplicationContext()));
                    try {
                        String fenPgnData = fenPgn.toString();
                        ArrayList<GameInfo> gi = PGNFile.getGameInfo(fenPgnData, 2);
                        if (gi.size() > 1) {
                            String fn = new File(StorageProvider.getPgnDir(), ".sharedfile.pgn").getAbsolutePath();
                            try (FileOutputStream writer = new FileOutputStream(fn)) {
                                writer.write(fenPgnData.getBytes());
                                writer.close();
                                loadPGNFromFile(fn);
                            } catch (IOException ex) {
                                ctrl.setFENOrPGN(fenPgnData, true);
                            }
                        } else {
                            ctrl.setFENOrPGN(fenPgnData, true);
                        }
                        setBoardFlip(true);
                    } catch (ChessParseError e) {
                        DroidFishApp.toast(getParseErrString(e), Toast.LENGTH_SHORT);
                    }
                }
                break;
            }
            }
        });
        return builder.create();
    }

    private Dialog boardMenuDialog() {
        final int CLIPBOARD        = 0;
        final int FILEMENU         = 1;
        final int SHARE_GAME       = 2;
        final int SHARE_TEXT       = 3;
        final int SHARE_IMAG       = 4;
        final int GET_FEN          = 5;
        final int REPEAT_LAST_MOVE = 6;

        setAutoMode(AutoMode.OFF);
        List<String> lst = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.clipboard));     actions.add(CLIPBOARD);
        if (storageAvailable()) {
            lst.add(getString(R.string.option_file));   actions.add(FILEMENU);
        }
        lst.add(getString(R.string.share_game));         actions.add(SHARE_GAME);
        lst.add(getString(R.string.share_text));         actions.add(SHARE_TEXT);
        lst.add(getString(R.string.share_image));        actions.add(SHARE_IMAG);
        if (hasFenProvider(getPackageManager())) {
            lst.add(getString(R.string.get_fen)); actions.add(GET_FEN);
        }
        if (moveAnnounceType.startsWith("speech_")) {
            lst.add(getString(R.string.repeat_last_move)); actions.add(REPEAT_LAST_MOVE);
        }
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.tools_menu);
        builder.setItems(lst.toArray(new String[0]), (dialog, item) -> {
            switch (actions.get(item)) {
            case CLIPBOARD:
                showDroidFishDialog(CLIPBOARD_DIALOG);
                break;
            case FILEMENU:
                reShowDialog(FILE_MENU_DIALOG);
                break;
            case SHARE_GAME:
                shareGameOrText(true);
                break;
            case SHARE_TEXT:
                shareGameOrText(false);
                break;
            case SHARE_IMAG:
                shareImage();
                break;
            case GET_FEN:
                getFen();
                break;
            case REPEAT_LAST_MOVE:
                speech.flushQueue();
                ctrl.repeatLastMove();
                break;
            }
        });
        return builder.create();
    }

    private void shareGameOrText(boolean game) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        i.setType(game ? "application/x-chess-pgn" : "text/plain");
        String pgn = ctrl.getPGN();
        if (pgn.length() < 32768) {
            i.putExtra(Intent.EXTRA_TEXT, pgn);
        } else {
            File dir = new File(getFilesDir(), "shared");
            dir.mkdirs();
            File file = new File(dir, game ? "game.pgn" : "game.txt");
            try (FileOutputStream fos = new FileOutputStream(file);
                 OutputStreamWriter ow = new OutputStreamWriter(fos, "UTF-8")) {
                ow.write(pgn);
            } catch (IOException e) {
                DroidFishApp.toast(e.getMessage(), Toast.LENGTH_LONG);
                return;
            }
            String authority = "org.petero.droidfish.modern.fileprovider";
            Uri uri = FileProvider.getUriForFile(this, authority, file);
            i.putExtra(Intent.EXTRA_STREAM, uri);
        }
        try {
            startActivity(Intent.createChooser(i, getString(game ? R.string.share_game :
                                                                   R.string.share_text)));
        } catch (ActivityNotFoundException ignore) {
        }
    }

    private void shareImage() {
        View v = findViewById(R.id.chessboard);
        int w = v.getWidth();
        int h = v.getHeight();
        if (w <= 0 || h <= 0)
            return;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        v.draw(c);
        File imgDir = new File(getFilesDir(), "shared");
        imgDir.mkdirs();
        File file = new File(imgDir, "screenshot.png");
        try {
            try (OutputStream os = new FileOutputStream(file)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
        } catch (IOException e) {
            DroidFishApp.toast(e.getMessage(), Toast.LENGTH_LONG);
            return;
        }

        String authority = "org.petero.droidfish.modern.fileprovider";
        Uri uri = FileProvider.getUriForFile(this, authority, file);

        Intent i = new Intent(Intent.ACTION_SEND);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.setType("image/png");
        try {
            startActivity(Intent.createChooser(i, getString(R.string.share_image)));
        } catch (ActivityNotFoundException ignore) {
        }
    }

    private Dialog fileMenuDialog() {
        final int LOAD_LAST_FILE    = 0;
        final int LOAD_GAME         = 1;
        final int LOAD_POS          = 2;
        final int LOAD_SCID_GAME    = 3;
        final int SAVE_GAME         = 4;
        final int LOAD_DELETED_GAME = 5;

        setAutoMode(AutoMode.OFF);
        List<String> lst = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        if (currFileType() != FT_NONE) {
            lst.add(getString(R.string.load_last_file)); actions.add(LOAD_LAST_FILE);
        }
        lst.add(getString(R.string.load_game));     actions.add(LOAD_GAME);
        lst.add(getString(R.string.load_position)); actions.add(LOAD_POS);
        if (hasScidProvider()) {
            lst.add(getString(R.string.load_scid_game)); actions.add(LOAD_SCID_GAME);
        }
        if (storageAvailable() && (new File(getAutoSaveFile())).exists()) {
            lst.add(getString(R.string.load_del_game));  actions.add(LOAD_DELETED_GAME);
        }
        lst.add(getString(R.string.save_game));     actions.add(SAVE_GAME);
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.load_save_menu);
        builder.setItems(lst.toArray(new String[0]), (dialog, item) -> {
            switch (actions.get(item)) {
            case LOAD_LAST_FILE:
                loadLastFile();
                break;
            case LOAD_GAME:
                selectFile(R.string.select_pgn_file, R.string.pgn_load, "currentPGNFile", pgnDir,
                              SELECT_PGN_FILE_DIALOG, RESULT_OI_PGN_LOAD);
                break;
            case SAVE_GAME:
                selectFile(R.string.select_pgn_file_save, R.string.pgn_save, "currentPGNFile", pgnDir,
                              SELECT_PGN_FILE_SAVE_DIALOG, RESULT_OI_PGN_SAVE);
                break;
            case LOAD_POS:
                selectFile(R.string.select_fen_file, R.string.pgn_load, "currentFENFile", fenDir,
                              SELECT_FEN_FILE_DIALOG, RESULT_OI_FEN_LOAD);
                break;
            case LOAD_SCID_GAME:
                selectScidFile();
                break;
            case LOAD_DELETED_GAME:
                loadPGNFromFile(getAutoSaveFile(), false);
                break;
            }
        });
        return builder.create();
    }

    /** Open dialog to select a game/position from the last used file. */
    private void loadLastFile() {
        String path = currPathName();
        if (path.length() == 0)
            return;
        setAutoMode(AutoMode.OFF);
        switch (currFileType()) {
        case FT_PGN:
            loadPGNFromFile(path);
            break;
        case FT_SCID: {
            Intent data = new Intent(path);
            onActivityResult(RESULT_SELECT_SCID, RESULT_OK, data);
            break;
        }
        case FT_FEN:
            loadFENFromFile(path);
            break;
        }
    }

    private Dialog aboutDialog() {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        WebView wv = new WebView(this);
        builder.setView(wv);
        try (InputStream is = getResources().openRawResource(R.raw.about)) {
            String data = FileUtil.readFromStream(is);
            if (data == null)
                data = "";
            wv.loadDataWithBaseURL(null, data, "text/html", "utf-8", null);
        } catch (IOException ignore) {
        }
        String title = getString(R.string.app_name);
        try {
            PackageInfo pi = getPackageManager().getPackageInfo("org.petero.droidfish", 0);
            title += " " + pi.versionName;
        } catch (NameNotFoundException ignore) {
        }
        builder.setTitle(title);
        return builder.create();
    }

    private Dialog selectBookDialog() {
        File bookDirFile = StorageProvider.getBookDir();
        String[] fileNames = FileUtil.findFilesInDirectory(bookDirFile, filename -> {
            int dotIdx = filename.lastIndexOf(".");
            if (dotIdx < 0)
                return false;
            String ext = filename.substring(dotIdx+1);
            return ("ctg".equals(ext) || "bin".equals(ext) || "abk".equals(ext));
        });
        final int numFiles = fileNames.length;
        final String[] items = new String[numFiles + 3];
        for (int i = 0; i < numFiles; i++)
            items[i] = fileNames[i];
        items[numFiles] = getString(R.string.internal_book);
        items[numFiles + 1] = getString(R.string.eco_book);
        items[numFiles + 2] = getString(R.string.no_book);

        int defaultItem = numFiles;
        if ("eco:".equals(bookOptions.filename))
            defaultItem = numFiles + 1;
        else if ("nobook:".equals(bookOptions.filename))
            defaultItem = numFiles + 2;
        String oldName = bookOptions.filename;
        String defDir = bookDirFile.getAbsolutePath() + File.separator;
        if (oldName.startsWith(defDir))
            oldName = oldName.substring(defDir.length());
        for (int i = 0; i < numFiles; i++) {
            if (oldName.equals(items[i])) {
                defaultItem = i;
                break;
            }
        }

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.select_opening_book_file);
        builder.setSingleChoiceItems(items, defaultItem, (dialog, item) -> {
            Editor editor = settings.edit();
            final String bookFile;
            if (item == numFiles)
                bookFile = "internal:";
            else if (item == numFiles + 1)
                bookFile = "eco:";
            else if (item == numFiles + 2)
                bookFile = "nobook:";
            else
                bookFile = items[item];
            editor.putString("bookFile", bookFile);
            editor.apply();
            bookOptions.filename = bookFile;
            setBookOptions();
            dialog.dismiss();
        });
        return builder.create();
    }

    private static boolean reservedEngineName(String name) {
        return "cuckoochess".equals(name) ||
               "stockfish".equals(name) ||
               name.endsWith(".ini");
    }

    private Dialog selectEngineDialog(final boolean abortOnCancel) {
        final ArrayList<String> items = new ArrayList<>();
        final ArrayList<String> ids = new ArrayList<>();
        ids.add("stockfish"); items.add(getString(R.string.stockfish_engine));
        ids.add("cuckoochess"); items.add(getString(R.string.cuckoochess_engine));

        if (storageAvailable()) {
            final File engineDirFile = StorageProvider.getEngineDir();
            final String base = engineDirFile.getAbsolutePath() + File.separator;
            {
                ChessEngineResolver resolver = new ChessEngineResolver(this);
                List<ChessEngine> engines = resolver.resolveEngines();
                ArrayList<Pair<String,String>> oexEngines = new ArrayList<>();
                for (ChessEngine engine : engines) {
                    if ((engine.getName() != null) && (engine.getFileName() != null) &&
                            (engine.getPackageName() != null)) {
                        oexEngines.add(new Pair<>(EngineUtil.openExchangeFileName(engine),
                                engine.getName()));
                    }
                }
                Collections.sort(oexEngines, (lhs, rhs) -> lhs.second.compareTo(rhs.second));
                for (Pair<String,String> eng : oexEngines) {
                    ids.add(base + EngineUtil.openExchangeDir + File.separator + eng.first);
                    items.add(eng.second);
                }
            }

            String[] fileNames = FileUtil.findFilesInDirectory(engineDirFile,
                                                               fname -> !reservedEngineName(fname));
            for (String file : fileNames) {
                ids.add(base + file);
                items.add(file);
            }
            if (fileNames.length == 0) {
                ids.add("");
                items.add("[Place engines in: " +
                    engineDirFile.getAbsolutePath() + "]");
            }
        }

        String currEngine = ctrl.getEngine();
        int defaultItem = 0;
        final int nEngines = items.size();
        for (int i = 0; i < nEngines; i++) {
            if (ids.get(i).equals(currEngine)) {
                defaultItem = i;
                break;
            }
        }
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.select_chess_engine);
        builder.setSingleChoiceItems(items.toArray(new String[0]), defaultItem,
                (dialog, item) -> {
                    if ((item < 0) || (item >= nEngines))
                        return;
                    Editor editor = settings.edit();
                    String engine = ids.get(item);
                    editor.putString("engine", engine);
                    editor.apply();
                    dialog.dismiss();
                    setEngineOptions(false);
                    setEngine(engine);
                });
        builder.setOnCancelListener(dialog -> {
            if (!abortOnCancel)
                reShowDialog(MANAGE_ENGINES_DIALOG);
        });
        return builder.create();
    }

    private interface Loader {
        void load(String pathName);
    }

    private Dialog selectPgnFileDialog() {
        return selectFileDialog(pgnDir, R.string.select_pgn_file, R.string.no_pgn_files,
                                "currentPGNFile", this::loadPGNFromFile);
    }

    private Dialog selectFenFileDialog() {
        return selectFileDialog(fenDir, R.string.select_fen_file, R.string.no_fen_files,
                                "currentFENFile", this::loadFENFromFile);
    }

    private Dialog selectFileDialog(final String defaultDir, int selectFileMsg, int noFilesMsg,
                                          String settingsName, final Loader loader) {
        setAutoMode(AutoMode.OFF);
        final String[] fileNames = FileUtil.findFilesInDirectory(defaultDir, null);
        final int numFiles = fileNames.length;
        if (numFiles == 0) {
            AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle(R.string.app_name).setMessage(noFilesMsg);
            return builder.create();
        }
        int defaultItem = 0;
        String currentFile = settings.getString(settingsName, "");
        currentFile = new File(currentFile).getName();
        for (int i = 0; i < numFiles; i++) {
            if (currentFile.equals(fileNames[i])) {
                defaultItem = i;
                break;
            }
        }
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(selectFileMsg);
        builder.setSingleChoiceItems(fileNames, defaultItem, (dialog, item) -> {
            dialog.dismiss();
            String sep = File.separator;
            String fn = fileNames[item];
            String pathName = new File(new File(StorageProvider.getBaseDir(), defaultDir), fn).getAbsolutePath();
            loader.load(pathName);
        });
        return builder.create();
    }

    private Dialog selectPgnFileSaveDialog() {
        setAutoMode(AutoMode.OFF);
        final String[] fileNames = FileUtil.findFilesInDirectory(pgnDir, null);
        final int numFiles = fileNames.length;
        int defaultItem = 0;
        String currentPGNFile = settings.getString("currentPGNFile", "");
        currentPGNFile = new File(currentPGNFile).getName();
        for (int i = 0; i < numFiles; i++) {
            if (currentPGNFile.equals(fileNames[i])) {
                defaultItem = i;
                break;
            }
        }
        final String[] items = new String[numFiles + 1];
        for (int i = 0; i < numFiles; i++)
            items[i] = fileNames[i];
        items[numFiles] = getString(R.string.new_file);
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.select_pgn_file_save);
        builder.setSingleChoiceItems(items, defaultItem, (dialog, item) -> {
            String pgnFile;
            if (item >= numFiles) {
                dialog.dismiss();
                showDroidFishDialog(SELECT_PGN_SAVE_NEWFILE_DIALOG);
            } else {
                dialog.dismiss();
                pgnFile = fileNames[item];
                String pathName = new File(StorageProvider.getPgnDir(), pgnFile).getAbsolutePath();
                savePGNToFile(pathName);
            }
        });
        return builder.create();
    }

    private Dialog selectPgnSaveNewFileDialog() {
        setAutoMode(AutoMode.OFF);
        View content = View.inflate(this, R.layout.create_pgn_file, null);
        final AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setView(content);
        builder.setTitle(R.string.select_pgn_file_save);
        final EditText fileNameView = content.findViewById(R.id.create_pgn_filename);
        fileNameView.setText("");
        final Runnable savePGN = () -> {
            String pgnFile = fileNameView.getText().toString();
            if ((pgnFile.length() > 0) && !pgnFile.contains("."))
                pgnFile += ".pgn";
            String pathName = new File(StorageProvider.getPgnDir(), pgnFile).getAbsolutePath();
            savePGNToFile(pathName);
        };
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> savePGN.run());
        builder.setNegativeButton(R.string.cancel, null);

        final Dialog dialog = builder.create();
        fileNameView.setOnKeyListener((v, keyCode, event) -> {
            if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                savePGN.run();
                dialog.cancel();
                return true;
            }
            return false;
        });
        return dialog;
    }

    private Dialog setColorThemeDialog() {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.select_color_theme);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);

        String[] themeNames = new String[ColorTheme.themeNames.length];
        for (int i = 0; i < themeNames.length; i++)
            themeNames[i] = getString(ColorTheme.themeNames[i]);

        final SeekBar brightnessBar = new SeekBar(this);
        brightnessBar.setMax(100);
        int savedProgress = (int)((settings.getFloat("boardBrightness", 1.0f) - 0.5f) * 100);
        brightnessBar.setProgress(Math.max(0, Math.min(100, savedProgress)));
        brightnessBar.setPadding(pad, pad / 2, pad, pad);
        brightnessBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float brightness = 0.5f + progress / 100.0f;
                    cb.setBoardBrightness(brightness);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float brightness = 0.5f + seekBar.getProgress() / 100.0f;
                Editor editor = settings.edit();
                editor.putFloat("boardBrightness", brightness);
                editor.apply();
            }
        });

        ListView listView = new ListView(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_single_choice, themeNames);
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            ColorTheme.instance().setTheme(settings, position);
            BoardTheme.instance().readSettings(settings);
            PieceSet.instance().readPrefs(settings);
            cb.setBoardBrightness(1.0f);
            brightnessBar.setProgress(50);
            Editor editor = settings.edit();
            editor.putFloat("boardBrightness", 1.0f);
            editor.apply();
            gameTextListener.clear();
            ctrl.prefsChanged(false);
            if (engineMatchController != null && engineMatchController.isRunning()) {
                cb.setMoveHints(null);
                cb.setSelection(-1);
            }
            overrideViewAttribs();
        });
        layout.addView(listView, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1.0f));

        TextView brightnessLabel = new TextView(this);
        brightnessLabel.setText("Board Brightness");
        brightnessLabel.setPadding(pad, pad, pad, 0);
        layout.addView(brightnessLabel);
        layout.addView(brightnessBar);

        builder.setView(layout);
        builder.setPositiveButton(android.R.string.ok, null);
        return builder.create();
    }

    private Dialog setBoardTextureDialog() {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.select_board_texture);
        String currentTexture = settings.getString("boardTexture", "");
        int checkedItem = 0;
        for (int i = 0; i < BoardTheme.TEXTURE_NAMES.length; i++) {
            if (BoardTheme.TEXTURE_NAMES[i].equals(currentTexture)) {
                checkedItem = i;
                break;
            }
        }
        builder.setSingleChoiceItems(BoardTheme.TEXTURE_DISPLAY_NAMES, checkedItem, (dialog, item) -> {
            Editor editor = settings.edit();
            editor.putString("boardTexture", BoardTheme.TEXTURE_NAMES[item]);
            editor.apply();
            BoardTheme.instance().readSettings(settings);
            cb.setColors();
            cb.invalidate();
            dialog.dismiss();
        });
        return builder.create();
    }

    private Dialog engineMatchDialog() {
        final ArrayList<String> engineItems = new ArrayList<>();
        final ArrayList<String> engineIds = new ArrayList<>();
        engineIds.add("stockfish"); engineItems.add(getString(R.string.stockfish_engine));
        engineIds.add("cuckoochess"); engineItems.add(getString(R.string.cuckoochess_engine));

        if (storageAvailable()) {
            final File engineDirFile = StorageProvider.getEngineDir();
            final String base = engineDirFile.getAbsolutePath() + File.separator;
            ChessEngineResolver resolver = new ChessEngineResolver(this);
            List<ChessEngine> engines = resolver.resolveEngines();
            ArrayList<Pair<String,String>> oexEngines = new ArrayList<>();
            for (ChessEngine engine : engines) {
                if ((engine.getName() != null) && (engine.getFileName() != null) &&
                        (engine.getPackageName() != null)) {
                    oexEngines.add(new Pair<>(EngineUtil.openExchangeFileName(engine),
                            engine.getName()));
                }
            }
            Collections.sort(oexEngines, (lhs, rhs) -> lhs.second.compareTo(rhs.second));
            for (Pair<String,String> eng : oexEngines) {
                engineIds.add(base + EngineUtil.openExchangeDir + File.separator + eng.first);
                engineItems.add(eng.second);
            }
            String[] fileNames = FileUtil.findFilesInDirectory(engineDirFile,
                    fname -> !reservedEngineName(fname));
            for (String file : fileNames) {
                engineIds.add(base + file);
                engineItems.add(file);
            }
        }

        final String[] engineNames = engineItems.toArray(new String[0]);

        // Build book list: bin/ctg/abk from book dir + pgn files from pgn dir
        final ArrayList<String> bookItems = new ArrayList<>();
        final ArrayList<String> bookPaths = new ArrayList<>();
        final ArrayList<EngineMatchConfig.BookType> bookTypes = new ArrayList<>();

        File bookDirForMatch = StorageProvider.getBookDir();
        String[] binBooks = FileUtil.findFilesInDirectory(bookDirForMatch, filename -> {
            int dotIdx = filename.lastIndexOf(".");
            if (dotIdx < 0) return false;
            String ext = filename.substring(dotIdx + 1);
            return ("ctg".equals(ext) || "bin".equals(ext) || "abk".equals(ext));
        });
        for (String book : binBooks) {
            bookItems.add(book);
            bookPaths.add(bookDirForMatch.getAbsolutePath() + File.separator + book);
            bookTypes.add(EngineMatchConfig.BookType.POLYGLOT);
        }

        // PGN books from book dir only
        String[] pgnInBookDir = FileUtil.findFilesInDirectory(bookDirForMatch, filename -> {
            return filename.toLowerCase(Locale.US).endsWith(".pgn");
        });
        for (String pgn : pgnInBookDir) {
            bookItems.add(pgn + " (PGN)");
            bookPaths.add(bookDirForMatch.getAbsolutePath() + File.separator + pgn);
            bookTypes.add(EngineMatchConfig.BookType.PGN);
        }

        android.view.LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.engine_match_dialog, null);

        final Spinner spinner1 = view.findViewById(R.id.engine_match_spinner1);
        final Spinner spinner2 = view.findViewById(R.id.engine_match_spinner2);
        final EditText timeEdit = view.findViewById(R.id.engine_match_time_edit);
        final EditText incEdit = view.findViewById(R.id.engine_match_inc_edit);
        final EditText gamesEdit = view.findViewById(R.id.engine_match_games_edit);
        final CheckBox alternateColors = view.findViewById(R.id.engine_match_alternate);
        final CheckBox keepScreenOnCb = view.findViewById(R.id.engine_match_keep_screen_on);
        final android.widget.RadioGroup openingGroup = view.findViewById(R.id.engine_match_opening_group);
        final View bookOptions = view.findViewById(R.id.engine_match_book_options);
        final Spinner bookSpinner = view.findViewById(R.id.engine_match_book_spinner);
        final EditText maxPliesEdit = view.findViewById(R.id.engine_match_max_plies_edit);
        final View pgnOrderRow = view.findViewById(R.id.engine_match_pgn_order_row);
        final Spinner pgnOrderSpinner = view.findViewById(R.id.engine_match_pgn_order_spinner);
        final EditText maxMovesEdit = view.findViewById(R.id.engine_match_max_moves_edit);
        final CheckBox autoAdjudicateCb = view.findViewById(R.id.engine_match_auto_adjudicate);
        final View adjudicationOptions = view.findViewById(R.id.engine_match_adjudication_options);
        final EditText drawCpEdit = view.findViewById(R.id.engine_match_draw_cp_edit);
        final EditText resignCpEdit = view.findViewById(R.id.engine_match_resign_cp_edit);

        autoAdjudicateCb.setOnCheckedChangeListener((btn, isChecked) -> {
            adjudicationOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, engineNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner1.setAdapter(adapter);
        spinner2.setAdapter(adapter);

        if (engineIds.size() > 1) spinner2.setSelection(1);

        android.widget.AdapterView.OnItemSelectedListener engineValidationListener =
                new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                String engineId = engineIds.get(pos);
                if ("stockfish".equals(engineId) || "cuckoochess".equals(engineId))
                    return;
                Spinner spinner = (Spinner) parent;
                new Thread(() -> {
                    boolean ok = validateEngine(engineId);
                    if (!ok) {
                        runOnUiThread(() -> {
                            DroidFishApp.toast("Engine load error! Pick a different engine.", Toast.LENGTH_LONG);
                            spinner.setSelection(0);
                        });
                    }
                }, "EngineValidation").start();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        };
        spinner1.setOnItemSelectedListener(engineValidationListener);
        spinner2.setOnItemSelectedListener(engineValidationListener);

        ArrayAdapter<String> bookAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, bookItems.toArray(new String[0]));
        bookAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bookSpinner.setAdapter(bookAdapter);

        String[] orderOptions = {
            getString(R.string.engine_match_pgn_sequential),
            getString(R.string.engine_match_pgn_random)
        };
        ArrayAdapter<String> orderAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, orderOptions);
        orderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pgnOrderSpinner.setAdapter(orderAdapter);

        android.widget.RadioButton bookRadio = view.findViewById(R.id.engine_match_radio_book);
        if (bookItems.isEmpty()) {
            bookRadio.setEnabled(false);
        }

        openingGroup.setOnCheckedChangeListener((group, checkedId) -> {
            bookOptions.setVisibility(checkedId == R.id.engine_match_radio_book ? View.VISIBLE : View.GONE);
        });

        bookSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                boolean isPgn = pos < bookTypes.size() && bookTypes.get(pos) == EngineMatchConfig.BookType.PGN;
                pgnOrderRow.setVisibility(isPgn ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                pgnOrderRow.setVisibility(View.GONE);
            }
        });

        timeEdit.setText("60");
        incEdit.setText("1");
        gamesEdit.setText("2");
        maxPliesEdit.setText("20");
        alternateColors.setChecked(true);

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.engine_match_title);
        builder.setView(view);
        builder.setPositiveButton(R.string.engine_match_start, (dialog, which) -> {
            EngineMatchConfig matchConfig = new EngineMatchConfig();
            int sel1 = spinner1.getSelectedItemPosition();
            int sel2 = spinner2.getSelectedItemPosition();
            matchConfig.engine1 = engineIds.get(sel1);
            matchConfig.engine2 = engineIds.get(sel2);
            matchConfig.engine1Name = engineNames[sel1];
            matchConfig.engine2Name = engineNames[sel2];

            try {
                matchConfig.timeMs = Integer.parseInt(timeEdit.getText().toString()) * 1000;
            } catch (NumberFormatException e) {
                matchConfig.timeMs = 60000;
            }
            try {
                matchConfig.incrementMs = Integer.parseInt(incEdit.getText().toString()) * 1000;
            } catch (NumberFormatException e) {
                matchConfig.incrementMs = 1000;
            }
            try {
                matchConfig.numGames = Integer.parseInt(gamesEdit.getText().toString());
            } catch (NumberFormatException e) {
                matchConfig.numGames = 2;
            }

            matchConfig.alternateColors = alternateColors.isChecked();
            matchConfig.keepScreenOn = keepScreenOnCb.isChecked();

            try {
                matchConfig.maxMoves = Integer.parseInt(maxMovesEdit.getText().toString());
            } catch (NumberFormatException e) {
                matchConfig.maxMoves = 200;
            }
            matchConfig.autoAdjudicate = autoAdjudicateCb.isChecked();
            if (matchConfig.autoAdjudicate) {
                try {
                    matchConfig.drawThresholdCp = Integer.parseInt(drawCpEdit.getText().toString());
                } catch (NumberFormatException e) {
                    matchConfig.drawThresholdCp = 10;
                }
                try {
                    matchConfig.resignThresholdCp = Integer.parseInt(resignCpEdit.getText().toString());
                } catch (NumberFormatException e) {
                    matchConfig.resignThresholdCp = 500;
                }
            }

            int checkedRadio = openingGroup.getCheckedRadioButtonId();
            if (checkedRadio == R.id.engine_match_radio_current_pos) {
                matchConfig.startFen = ctrl.getFEN();
            } else if (checkedRadio == R.id.engine_match_radio_book) {
                int bookSel = bookSpinner.getSelectedItemPosition();
                if (bookSel >= 0 && bookSel < bookTypes.size()) {
                    matchConfig.bookType = bookTypes.get(bookSel);
                    matchConfig.bookFile = bookPaths.get(bookSel);
                    try {
                        matchConfig.pgnBookMaxPlies = Integer.parseInt(maxPliesEdit.getText().toString());
                    } catch (NumberFormatException e) {
                        matchConfig.pgnBookMaxPlies = 20;
                    }
                    if (matchConfig.bookType == EngineMatchConfig.BookType.PGN) {
                        matchConfig.pgnBookOrder = pgnOrderSpinner.getSelectedItemPosition() == 0 ?
                                org.petero.droidfish.book.PgnBook.Order.SEQUENTIAL :
                                org.petero.droidfish.book.PgnBook.Order.RANDOM;
                    }
                }
            }

            startEngineMatch(matchConfig);
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        return builder.create();
    }

    private void startEngineMatch(EngineMatchConfig matchConfig) {
        EngineOptions eOpts = new EngineOptions();
        eOpts.workDir = StorageProvider.getEngineDir().getAbsolutePath();
        Handler handler = new Handler(Looper.getMainLooper());

        EngineMatchListener matchListener = new EngineMatchListener() {
            @Override
            public void onMatchStarted(EngineMatchConfig config) {
                handler.post(() -> {
                    status.setText(getString(R.string.engine_match_running));
                });
            }

            @Override
            public void onGameStarted(int gameNumber, String whiteEngine, String blackEngine) {
                handler.post(() -> {
                    matchMoveText = new StringBuilder();
                    moveList.setText("");
                    engineTitleText.setText(String.format(Locale.US, "Engine Match (%d/%d)",
                            gameNumber, matchConfig.numGames));
                    status.setText(String.format(Locale.US, "Game %d: %s vs %s",
                            gameNumber, whiteEngine, blackEngine));
                });
            }

            @Override
            public void onPositionChanged(org.petero.droidfish.gamelogic.Position pos,
                                          org.petero.droidfish.gamelogic.Move lastMove,
                                          String lastMoveNotation,
                                          String whiteEngine, String blackEngine,
                                          long wTimeMs, long bTimeMs) {
                handler.post(() -> {
                    cb.setPosition(pos);
                    if (lastMove != null) {
                        cb.setLastMoveArrow(lastMove);
                    } else {
                        cb.clearLastMoveArrow();
                    }
                    cb.invalidate();
                    String wName = whiteEngine.length() > 10 ? whiteEngine.substring(0, 10) : whiteEngine;
                    String bName = blackEngine.length() > 10 ? blackEngine.substring(0, 10) : blackEngine;
                    whiteTitleText.setText(getString(R.string.white_square_character) + " " +
                            wName + " " + timeToString((int) wTimeMs));
                    blackTitleText.setText(getString(R.string.black_square_character) + " " +
                            bName + " " + timeToString((int) bTimeMs));

                    if (matchMoveText != null && lastMoveNotation != null) {
                        if (pos.whiteMove) {
                            // Black just moved
                            matchMoveText.append(lastMoveNotation).append(" ");
                        } else {
                            // White just moved
                            matchMoveText.append(pos.fullMoveCounter).append(". ").append(lastMoveNotation).append(" ");
                        }
                        moveList.setText(matchMoveText.toString());
                        moveListScroll.post(() -> moveListScroll.fullScroll(View.FOCUS_DOWN));
                    }
                });
            }

            @Override
            public void onEngineThinking(String engineName, int depth, int score, String pv) {
                handler.post(() -> {
                    String info = String.format(Locale.US, "%s d%d %+.2f %s",
                            engineName, depth, score / 100.0, pv);
                    thinking.setText(info);
                    thinking.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onGameFinished(EngineMatchResult.GameRecord record) {
                handler.post(() -> {
                    EngineMatchResult res = engineMatchController.getResult();
                    matchScoreLine.setText(String.format(Locale.US, "%s: +%d -%d =%d",
                            matchConfig.engine1Name, res.getEngine1Wins(), res.getEngine2Wins(), res.getDraws()));
                    status.setText(String.format(Locale.US, "Game %d: %s (%s)",
                            record.gameNumber, record.resultString(), record.termination));
                });
            }

            @Override
            public void onMatchFinished(EngineMatchResult result, String pgnFilePath) {
                handler.post(() -> {
                    setEngineMatchMode(false, false);
                    showMatchResultDialog(result, pgnFilePath);
                });
            }

            @Override
            public void onMatchError(String error) {
                handler.post(() -> {
                    new MaterialAlertDialogBuilder(DroidFish.this)
                        .setTitle(R.string.engine_error)
                        .setMessage(error)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                    status.setText(error);
                    setEngineMatchMode(false, false);
                });
            }

            @Override
            public void onStatusUpdate(String statusMsg) {
                handler.post(() -> status.setText(statusMsg));
            }
        };

        if (engineMatchController != null && engineMatchController.isRunning()) {
            engineMatchController.stopMatch();
        }
        ctrl.shutdownEngine();

        engineMatchController = new EngineMatchController(matchListener, eOpts);
        setEngineMatchMode(true, matchConfig.keepScreenOn);
        engineMatchController.startMatch(matchConfig);
    }

    private String savedEngineTitleText = "";

    private void setEngineMatchMode(boolean active, boolean keepScreenOn) {
        if (active) {
            custom1Button.setVisibility(View.GONE);
            custom2Button.setVisibility(View.GONE);
            custom3Button.setVisibility(View.GONE);
            modeButton.setVisibility(View.GONE);
            adjudicateButton.setVisibility(View.VISIBLE);
            stopMatchButton.setVisibility(View.VISIBLE);
            savedEngineTitleText = engineTitleText.getText().toString();
            engineTitleText.setText("Engine Match");
            cb.setSelection(-1);
            cb.setMoveHints(null);
            evalBar.setVisibility(View.GONE);
            cb.invalidate();
            matchScoreLine.setText("");
            matchScoreLine.setVisibility(View.VISIBLE);
            updateMatchButtons();
            acquireMatchWakeLock(keepScreenOn);
        } else {
            custom1Button.setVisibility(View.VISIBLE);
            custom2Button.setVisibility(View.VISIBLE);
            custom3Button.setVisibility(View.VISIBLE);
            modeButton.setVisibility(View.VISIBLE);
            adjudicateButton.setVisibility(View.GONE);
            stopMatchButton.setVisibility(View.GONE);
            engineTitleText.setText(savedEngineTitleText);
            cb.clearLastMoveArrow();
            matchScoreLine.setVisibility(View.GONE);
            updateEvalBarVisibility();
            releaseMatchWakeLock();
        }
    }

    private void updateMatchButtons() {
        Resources r = getResources();
        int bWidth = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, r.getDisplayMetrics()));
        int bHeight = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, r.getDisplayMetrics()));
        SVG svg = null;
        try { svg = SVG.getFromResource(getResources(), R.raw.touch); } catch (SVGParseException ignore) {}
        setButtonData(adjudicateButton, bWidth, bHeight, R.raw.adjudicate, svg);
        setButtonData(stopMatchButton, bWidth, bHeight, R.raw.stop, svg);
    }

    private PowerManager.WakeLock matchCpuWakeLock;

    private void acquireMatchWakeLock(boolean keepScreenOn) {
        if (keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            matchCpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DroidFish:EngineMatch");
            matchCpuWakeLock.acquire();
        }
    }

    private void releaseMatchWakeLock() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (matchCpuWakeLock != null && matchCpuWakeLock.isHeld()) {
            matchCpuWakeLock.release();
            matchCpuWakeLock = null;
        }
    }

    private void showAdjudicateDialog() {
        if (engineMatchController == null || !engineMatchController.isRunning())
            return;
        String[] options = {
            getString(R.string.engine_match_white_wins),
            getString(R.string.engine_match_draw),
            getString(R.string.engine_match_black_wins)
        };
        final int[] selected = {1}; // default to draw
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.engine_match_adjudicate_title)
            .setSingleChoiceItems(options, 1, (dialog, which) -> selected[0] = which)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                EngineMatchController.AdjudicationResult adjResult;
                switch (selected[0]) {
                    case 0: adjResult = EngineMatchController.AdjudicationResult.WHITE_WINS; break;
                    case 2: adjResult = EngineMatchController.AdjudicationResult.BLACK_WINS; break;
                    default: adjResult = EngineMatchController.AdjudicationResult.DRAW; break;
                }
                engineMatchController.adjudicateGame(adjResult);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showMatchResultDialog(EngineMatchResult result, String pgnFilePath) {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Match Complete");

        StringBuilder sb = new StringBuilder();
        sb.append(result.getSummary()).append("\n\n");

        if (result.getTotalGames() % 2 == 0 && result.getTotalGames() >= 2) {
            sb.append("Pentanomial: ").append(result.getPentanomial()).append("\n\n");
        }

        String elo = result.getEloEstimate();
        if (!elo.isEmpty()) {
            sb.append(elo).append("\n\n");
        }

        if (pgnFilePath != null) {
            sb.append("PGN saved to:\n").append(pgnFilePath);
        }
        builder.setMessage(sb.toString());
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    private boolean validateEngine(String engineId) {
        EngineOptions eOpts = new EngineOptions();
        eOpts.workDir = StorageProvider.getEngineDir().getAbsolutePath();
        UCIEngine engine = null;
        try {
            engine = UCIEngineBase.getEngine(engineId, eOpts, errMsg -> {});
            engine.initialize();
            engine.writeLineToEngine("uci");
            long deadline = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < deadline) {
                String line = engine.readLineFromEngine(200);
                if (line == null) continue;
                if (line.equals("uciok")) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (engine != null) {
                try { engine.writeLineToEngine("quit"); } catch (Exception ignored) {}
                engine.shutDown();
            }
        }
    }

    private Dialog gameModeDialog() {
        final String[] items = {
            getString(R.string.analysis_mode),
            getString(R.string.edit_replay_game),
            getString(R.string.play_white),
            getString(R.string.play_black),
            getString(R.string.two_players),
            getString(R.string.comp_vs_comp)
        };
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.select_game_mode);
        builder.setItems(items, (dialog, item) -> {
            int gameModeType = -1;
            boolean matchPlayerNames = false;
            switch (item) {
            case 0: gameModeType = GameMode.ANALYSIS;      break;
            case 1: gameModeType = GameMode.EDIT_GAME;     break;
            case 2: gameModeType = GameMode.PLAYER_WHITE; matchPlayerNames = true; break;
            case 3: gameModeType = GameMode.PLAYER_BLACK; matchPlayerNames = true; break;
            case 4: gameModeType = GameMode.TWO_PLAYERS;   break;
            case 5: gameModeType = GameMode.TWO_COMPUTERS; break;
            default: break;
            }
            dialog.dismiss();
            if (gameModeType >= 0) {
                newGameMode(gameModeType);
                setBoardFlip(matchPlayerNames);
            }
        });
        return builder.create();
    }

    private Dialog moveListMenuDialog() {
        final int EDIT_HEADERS   = 0;
        final int EDIT_COMMENTS  = 1;
        final int ADD_ECO        = 2;
        final int REMOVE_SUBTREE = 3;
        final int MOVE_VAR_UP    = 4;
        final int MOVE_VAR_DOWN  = 5;
        final int ADD_NULL_MOVE  = 6;

        setAutoMode(AutoMode.OFF);
        List<String> lst = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.edit_headers));      actions.add(EDIT_HEADERS);
        if (ctrl.humansTurn()) {
            lst.add(getString(R.string.edit_comments)); actions.add(EDIT_COMMENTS);
        }
        lst.add(getString(R.string.add_eco));           actions.add(ADD_ECO);
        lst.add(getString(R.string.truncate_gametree)); actions.add(REMOVE_SUBTREE);
        if (ctrl.canMoveVariationUp()) {
            lst.add(getString(R.string.move_var_up));   actions.add(MOVE_VAR_UP);
        }
        if (ctrl.canMoveVariationDown()) {
            lst.add(getString(R.string.move_var_down)); actions.add(MOVE_VAR_DOWN);
        }

        boolean allowNullMove =
            (gameMode.analysisMode() ||
             (gameMode.playerWhite() && gameMode.playerBlack() && !gameMode.clocksActive())) &&
             !ctrl.inCheck();
        if (allowNullMove) {
            lst.add(getString(R.string.add_null_move)); actions.add(ADD_NULL_MOVE);
        }
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.edit_game);
        builder.setItems(lst.toArray(new String[0]), (dialog, item) -> {
            switch (actions.get(item)) {
            case EDIT_HEADERS:
                editHeaders();
                break;
            case EDIT_COMMENTS:
                editComments();
                break;
            case ADD_ECO:
                ctrl.addECO();
                break;
            case REMOVE_SUBTREE:
                ctrl.removeSubTree();
                break;
            case MOVE_VAR_UP:
                ctrl.moveVariation(-1);
                break;
            case MOVE_VAR_DOWN:
                ctrl.moveVariation(1);
                break;
            case ADD_NULL_MOVE:
                ctrl.makeHumanNullMove();
                break;
            }
            moveListMenuDlg = null;
        });
        AlertDialog alert = builder.create();
        moveListMenuDlg = alert;
        return alert;
    }

    /** Let the user edit the PGN headers. */
    private void editHeaders() {
        final TreeMap<String,String> headers = new TreeMap<>();
        ctrl.getHeaders(headers);

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(DroidFish.this);
        builder.setTitle(R.string.edit_headers);
        View content = View.inflate(DroidFish.this, R.layout.edit_headers, null);
        builder.setView(content);

        final TextView event, site, date, round, white, black;

        event = content.findViewById(R.id.ed_header_event);
        site = content.findViewById(R.id.ed_header_site);
        date = content.findViewById(R.id.ed_header_date);
        round = content.findViewById(R.id.ed_header_round);
        white = content.findViewById(R.id.ed_header_white);
        black = content.findViewById(R.id.ed_header_black);

        event.setText(headers.get("Event"));
        site .setText(headers.get("Site"));
        date .setText(headers.get("Date"));
        round.setText(headers.get("Round"));
        white.setText(headers.get("White"));
        black.setText(headers.get("Black"));

        final Spinner gameResult = content.findViewById(R.id.ed_game_result);
        final String[] items = new String[]{"1-0", "1/2-1/2", "0-1", "*"};
        ArrayAdapter<String> adapt =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapt.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gameResult.setAdapter(adapt);
        gameResult.setSelection(Arrays.asList(items).indexOf(headers.get("Result")));

        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            headers.put("Event", event.getText().toString().trim());
            headers.put("Site",  site .getText().toString().trim());
            headers.put("Date",  date .getText().toString().trim());
            headers.put("Round", round.getText().toString().trim());
            headers.put("White", white.getText().toString().trim());
            headers.put("Black", black.getText().toString().trim());
            int p = gameResult.getSelectedItemPosition();
            String res = (p >= 0 && p < items.length) ? items[p] : "";
            if (!res.isEmpty())
                headers.put("Result", res);
            ctrl.setHeaders(headers);
            setBoardFlip(true);
        });

        builder.show();
    }

    /** Let the user edit comments related to a move. */
    private void editComments() {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(DroidFish.this);
        builder.setTitle(R.string.edit_comments);
        View content = View.inflate(DroidFish.this, R.layout.edit_comments, null);
        builder.setView(content);

        Game.CommentInfo commInfo = ctrl.getComments();

        final TextView preComment, moveView, nag, postComment;
        preComment = content.findViewById(R.id.ed_comments_pre);
        moveView = content.findViewById(R.id.ed_comments_move);
        nag = content.findViewById(R.id.ed_comments_nag);
        postComment = content.findViewById(R.id.ed_comments_post);

        preComment.setText(commInfo.preComment);
        postComment.setText(commInfo.postComment);
        moveView.setText(commInfo.move);
        String nagStr = Node.nagStr(commInfo.nag).trim();
        if ((nagStr.length() == 0) && (commInfo.nag > 0))
            nagStr = String.format(Locale.US, "%d", commInfo.nag);
        nag.setText(nagStr);

        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            String pre = preComment.getText().toString().trim();
            String post = postComment.getText().toString().trim();
            int nagVal = Node.strToNag(nag.getText().toString());

            commInfo.preComment = pre;
            commInfo.postComment = post;
            commInfo.nag = nagVal;
            ctrl.setComments(commInfo);
        });

        builder.show();
    }

    private Dialog thinkingMenuDialog() {
        final int ADD_ANALYSIS      = 0;
        final int COPY_TO_CLIPBOARD = 1;
        final int MULTIPV_SET       = 2;
        final int SHOW_WHOLE_VARS   = 3;
        final int TRUNCATE_VARS     = 4;
        final int HIDE_STATISTICS   = 5;
        final int SHOW_STATISTICS   = 6;
        List<String> lst = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.add_analysis));      actions.add(ADD_ANALYSIS);
        lst.add(getString(R.string.copy_to_clipboard)); actions.add(COPY_TO_CLIPBOARD);
        int numPV = this.numPV;
        final int maxPV = ctrl.maxPV();
        if (gameMode.analysisMode()) {
            numPV = Math.min(Math.max(numPV, 1), maxPV);
            if (maxPV > 1) {
                lst.add(getString(R.string.num_variations)); actions.add(MULTIPV_SET);
            }
        }
        final int numPVF = numPV;
        if (thinkingStr1.length() > 0) {
            if (fullPVLines) {
                lst.add(getString(R.string.truncate_variations)); actions.add(TRUNCATE_VARS);
            } else {
                lst.add(getString(R.string.show_whole_variations)); actions.add(SHOW_WHOLE_VARS);
            }
            if (mShowStats) {
                lst.add(getString(R.string.hide_statistics)); actions.add(HIDE_STATISTICS);
            } else {
                lst.add(getString(R.string.show_statistics)); actions.add(SHOW_STATISTICS);
            }
        }
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.analysis);
        builder.setItems(lst.toArray(new String[0]), (dialog, item) -> {
            switch (actions.get(item)) {
            case ADD_ANALYSIS: {
                ArrayList<ArrayList<Move>> pvMovesTmp = pvMoves;
                String[] pvStrs = thinkingStr1.split("\n");
                for (int i = 0; i < pvMovesTmp.size(); i++) {
                    ArrayList<Move> pv = pvMovesTmp.get(i);
                    StringBuilder preComment = new StringBuilder();
                    if (i < pvStrs.length) {
                        String[] tmp = pvStrs[i].split(" ");
                        for (int j = 0; j < 2; j++) {
                            if (j < tmp.length) {
                                if (j > 0) preComment.append(' ');
                                preComment.append(tmp[j]);
                            }
                        }
                        if (preComment.length() > 0) preComment.append(':');
                    }
                    boolean updateDefault = (i == 0);
                    ctrl.addVariation(preComment.toString(), pv, updateDefault);
                }
                break;
            }
            case COPY_TO_CLIPBOARD: {
                StringBuilder sb = new StringBuilder();
                if (!thinkingStr1.isEmpty()) {
                    sb.append(thinkingStr1);
                    if (!thinkingStr2.isEmpty())
                        sb.append('\n');
                }
                sb.append(thinkingStr2);
                ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                ClipData cd = new ClipData("DroidFish analysis",
                                           new String[]{ ClipDescription.MIMETYPE_TEXT_PLAIN },
                                           new ClipData.Item(sb.toString()));
                clipboard.setPrimaryClip(cd);
                break;
            }
            case MULTIPV_SET: {
                MultiPVSet m = new MultiPVSet();
                m.multiPVDialog(numPVF, maxPV);
                break;
            }
            case SHOW_WHOLE_VARS:
            case TRUNCATE_VARS: {
                fullPVLines = actions.get(item) == SHOW_WHOLE_VARS;
                Editor editor = settings.edit();
                editor.putBoolean("fullPVLines", fullPVLines);
                editor.apply();
                updateThinkingInfo();
                break;
            }
            case HIDE_STATISTICS:
            case SHOW_STATISTICS: {
                mShowStats = actions.get(item) == SHOW_STATISTICS;
                Editor editor = settings.edit();
                editor.putBoolean("showStats", mShowStats);
                editor.apply();
                updateThinkingInfo();
                break;
            }
            }
        });
        return builder.create();
    }

    /** Handle user interface to set MultiPV value. */
    private class MultiPVSet {
        private void setMultiPVMode(int nPV) {
            numPV = nPV;
            Editor editor = settings.edit();
            editor.putInt("numPV", numPV);
            editor.apply();
            ctrl.setMultiPVMode(numPV);
        }

        private int maxProgress(int maxPV) { // [1,maxPV] -> [0, maxProgress]
            return (maxPV - 1) * 10;
        }

        private int progressToNumPV(int p, int maxPV) {
            int maxProg = maxProgress(maxPV);
            p = Math.max(0, p);
            p = Math.min(maxProg, p);
            double x = p / (double)maxProg;
            return (int)Math.round(x * x * (maxPV - 1) + 1);
        }

        private int numPVToProgress(int nPV, int maxPV) {
            nPV = Math.max(1, nPV);
            nPV = Math.min(maxPV, nPV);
            double x = Math.sqrt((nPV - 1) / (double)(maxPV - 1));
            return (int)Math.round(x * maxProgress(maxPV));
        }
        
        private void updateText(EditText editTxt, int nPV) {
            String txt = Integer.valueOf(nPV).toString();
            if (!txt.equals(editTxt.getText().toString())) {
                editTxt.setText(txt);
                editTxt.setSelection(txt.length());
            }
        }

        /** Ask user what MultiPV value to use. */
        public void multiPVDialog(int numPV, int maxPV0) {
            final int maxPV = Math.min(100, maxPV0);
            numPV = Math.min(maxPV, numPV);

            AlertDialog.Builder builder = new MaterialAlertDialogBuilder(DroidFish.this);
            builder.setTitle(R.string.num_variations);
            View content = View.inflate(DroidFish.this, R.layout.num_variations, null);
            builder.setView(content);

            final SeekBar seekBar = content.findViewById(R.id.numvar_seekbar);
            final EditText editTxt = content.findViewById(R.id.numvar_edittext);

            seekBar.setMax(numPVToProgress(maxPV, maxPV));
            seekBar.setProgress(numPVToProgress(numPV, maxPV));
            updateText(editTxt, numPV);

            seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) { }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int nPV = progressToNumPV(progress, maxPV);
                    updateText(editTxt, nPV);
                }
            });
            editTxt.addTextChangedListener(new TextWatcher() {
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String txt = editTxt.getText().toString();
                    try {
                        int nPV = Integer.parseInt(txt);
                        int p = numPVToProgress(nPV, maxPV);
                        if (p != seekBar.getProgress())
                            seekBar.setProgress(p);
                        updateText(editTxt, progressToNumPV(p, maxPV));
                        
                    } catch (NumberFormatException ignore) {
                    }
                }
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override
                public void afterTextChanged(Editable s) { }
            });

            builder.setNegativeButton(R.string.cancel, null);
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                int p = seekBar.getProgress();
                int nPV = progressToNumPV(p, maxPV);
                setMultiPVMode(nPV);
            });

            builder.show();
        }
    }

    private Dialog goBackMenuDialog() {
        final int GOTO_START_GAME = 0;
        final int GOTO_START_VAR  = 1;
        final int GOTO_PREV_VAR   = 2;
        final int LOAD_PREV_GAME  = 3;
        final int AUTO_BACKWARD   = 4;

        setAutoMode(AutoMode.OFF);
        List<String> lst = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.goto_start_game));      actions.add(GOTO_START_GAME);
        lst.add(getString(R.string.goto_start_variation)); actions.add(GOTO_START_VAR);
        if (ctrl.currVariation() > 0) {
            lst.add(getString(R.string.goto_prev_variation)); actions.add(GOTO_PREV_VAR);
        }
        final UIAction prevGame = actionFactory.getAction("prevGame");
        if (prevGame.enabled()) {
            lst.add(getString(R.string.load_prev_game)); actions.add(LOAD_PREV_GAME);
        }
        if (!gameMode.clocksActive()) {
            lst.add(getString(R.string.auto_backward)); actions.add(AUTO_BACKWARD);
        }
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.go_back);
        builder.setItems(lst.toArray(new String[0]), (dialog, item) -> {
            switch (actions.get(item)) {
            case GOTO_START_GAME: ctrl.gotoMove(0); break;
            case GOTO_START_VAR:  ctrl.gotoStartOfVariation(); break;
            case GOTO_PREV_VAR:   ctrl.changeVariation(-1); break;
            case LOAD_PREV_GAME:
                prevGame.run();
                break;
            case AUTO_BACKWARD:
                setAutoMode(AutoMode.BACKWARD);
                break;
            }
        });
        return builder.create();
    }

    private Dialog goForwardMenuDialog() {
        final int GOTO_END_VAR   = 0;
        final int GOTO_NEXT_VAR  = 1;
        final int LOAD_NEXT_GAME = 2;
        final int AUTO_FORWARD   = 3;

        setAutoMode(AutoMode.OFF);
        List<String> lst = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.goto_end_variation)); actions.add(GOTO_END_VAR);
        if (ctrl.currVariation() < ctrl.numVariations() - 1) {
            lst.add(getString(R.string.goto_next_variation)); actions.add(GOTO_NEXT_VAR);
        }
        final UIAction nextGame = actionFactory.getAction("nextGame");
        if (nextGame.enabled()) {
            lst.add(getString(R.string.load_next_game)); actions.add(LOAD_NEXT_GAME);
        }
        if (!gameMode.clocksActive()) {
            lst.add(getString(R.string.auto_forward)); actions.add(AUTO_FORWARD);
        }
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.go_forward);
        builder.setItems(lst.toArray(new String[0]), (dialog, item) -> {
            switch (actions.get(item)) {
            case GOTO_END_VAR:  ctrl.gotoMove(Integer.MAX_VALUE); break;
            case GOTO_NEXT_VAR: ctrl.changeVariation(1); break;
            case LOAD_NEXT_GAME:
                nextGame.run();
                break;
            case AUTO_FORWARD:
                setAutoMode(AutoMode.FORWARD);
                break;
            }
        });
        return builder.create();
    }

    private Dialog makeButtonDialog(ButtonActions buttonActions) {
        List<String> names = new ArrayList<>();
        final List<UIAction> actions = new ArrayList<>();

        HashSet<String> used = new HashSet<>();
        for (UIAction a : buttonActions.getMenuActions()) {
            if ((a != null) && a.enabled() && !used.contains(a.getId())) {
                names.add(getString(a.getName()));
                actions.add(a);
                used.add(a.getId());
            }
        }
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(buttonActions.getMenuTitle());
        builder.setItems(names.toArray(new String[0]), (dialog, item) -> {
            UIAction a = actions.get(item);
            a.run();
        });
        return builder.create();
    }

    private Dialog manageEnginesDialog() {
        final int SELECT_ENGINE = 0;
        final int SET_ENGINE_OPTIONS = 1;
        final int CONFIG_NET_ENGINE = 2;
        List<String> lst = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.select_engine)); actions.add(SELECT_ENGINE);
        if (canSetEngineOptions()) {
            lst.add(getString(R.string.set_engine_options));
            actions.add(SET_ENGINE_OPTIONS);
        }
        lst.add(getString(R.string.configure_network_engine)); actions.add(CONFIG_NET_ENGINE);
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.option_manage_engines);
        builder.setItems(lst.toArray(new String[0]), (dialog, item) -> {
            switch (actions.get(item)) {
            case SELECT_ENGINE:
                reShowDialog(SELECT_ENGINE_DIALOG);
                break;
            case SET_ENGINE_OPTIONS:
                setEngineOptions();
                break;
            case CONFIG_NET_ENGINE:
                reShowDialog(NETWORK_ENGINE_DIALOG);
                break;
            }
        });
        return builder.create();
    }

    /** Return true if engine UCI options can be set now. */
    private boolean canSetEngineOptions() {
        if (!storageAvailable())
            return false;
        UCIOptions uciOpts = ctrl.getUCIOptions();
        if (uciOpts == null)
            return false;
        for (String name : uciOpts.getOptionNames())
            if (uciOpts.getOption(name).visible)
                return true;
        return false;
    }

    /** Start activity to set engine options. */
    private void setEngineOptions() {
        Intent i = new Intent(DroidFish.this, EditOptions.class);
        UCIOptions uciOpts = ctrl.getUCIOptions();
        if (uciOpts != null) {
            i.putExtra("org.petero.droidfish.ucioptions", uciOpts);
            i.putExtra("org.petero.droidfish.enginename", engineTitleText.getText().toString());
            i.putExtra("org.petero.droidfish.workDir", engineOptions.workDir);
            boolean localEngine = engineOptions.networkID.isEmpty();
            i.putExtra("org.petero.droidfish.localEngine", localEngine);
            startActivityForResult(i, RESULT_EDITOPTIONS);
        }
    }

    private Dialog networkEngineDialog() {
        File networkEngineDir = StorageProvider.getEngineDir();
        String[] fileNames = FileUtil.findFilesInDirectory(networkEngineDir, filename -> {
            if (reservedEngineName(filename))
                return false;
            return EngineUtil.isNetEngine(filename);
        });
        final int numItems = fileNames.length + 1;
        final String[] items = new String[numItems];
        final String[] ids = new String[numItems];
        int idx = 0;
        String base = networkEngineDir.getAbsolutePath() + File.separator;
        for (String fileName : fileNames) {
            ids[idx] = base + fileName;
            items[idx] = fileName;
            idx++;
        }
        ids[idx] = ""; items[idx] = getString(R.string.new_engine); idx++;
        String currEngine = ctrl.getEngine();
        int defaultItem = 0;
        for (int i = 0; i < numItems; i++)
            if (ids[i].equals(currEngine)) {
                defaultItem = i;
                break;
            }
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.configure_network_engine);
        builder.setSingleChoiceItems(items, defaultItem, (dialog, item) -> {
            if ((item < 0) || (item >= numItems))
                return;
            dialog.dismiss();
            if (item == numItems - 1) {
                showDroidFishDialog(NEW_NETWORK_ENGINE_DIALOG);
            } else {
                networkEngineToConfig = ids[item];
                reShowDialog(NETWORK_ENGINE_CONFIG_DIALOG);
            }
        });
        builder.setOnCancelListener(dialog -> reShowDialog(MANAGE_ENGINES_DIALOG));
        return builder.create();
    }

    // Filename of network engine to configure
    private String networkEngineToConfig = "";

    // Ask for name of new network engine
    private Dialog newNetworkEngineDialog() {
        View content = View.inflate(this, R.layout.create_network_engine, null);
        final AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setView(content);
        builder.setTitle(R.string.create_network_engine);
        final EditText engineNameView = content.findViewById(R.id.create_network_engine);
        engineNameView.setText("");
        final Runnable createEngine = () -> {
            String engineName = engineNameView.getText().toString();
            String pathName = new File(StorageProvider.getEngineDir(), engineName).getAbsolutePath();
            File file = new File(pathName);
            boolean nameOk = true;
            int errMsg = -1;
            if (engineName.contains("/")) {
                nameOk = false;
                errMsg = R.string.slash_not_allowed;
            } else if (reservedEngineName(engineName) || file.exists()) {
                nameOk = false;
                errMsg = R.string.engine_name_in_use;
            }
            if (!nameOk) {
                DroidFishApp.toast(errMsg, Toast.LENGTH_LONG);
                reShowDialog(NETWORK_ENGINE_DIALOG);
                return;
            }
            networkEngineToConfig = pathName;
            reShowDialog(NETWORK_ENGINE_CONFIG_DIALOG);
        };
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> createEngine.run());
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> reShowDialog(NETWORK_ENGINE_DIALOG));
        builder.setOnCancelListener(dialog -> reShowDialog(NETWORK_ENGINE_DIALOG));

        final Dialog dialog = builder.create();
        engineNameView.setOnKeyListener((v, keyCode, event) -> {
            if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                createEngine.run();
                dialog.cancel();
                return true;
            }
            return false;
        });
        return dialog;
    }

    // Configure network engine settings
    private Dialog networkEngineConfigDialog() {
        View content = View.inflate(this, R.layout.network_engine_config, null);
        final AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setView(content);
        builder.setTitle(R.string.configure_network_engine);
        final EditText hostNameView = content.findViewById(R.id.network_engine_host);
        final EditText portView = content.findViewById(R.id.network_engine_port);
        String hostName = "";
        String port = "0";
        try {
            if (EngineUtil.isNetEngine(networkEngineToConfig)) {
                String[] lines = FileUtil.readFile(networkEngineToConfig);
                if (lines.length > 1)
                    hostName = lines[1];
                if (lines.length > 2)
                    port = lines[2];
            }
        } catch (IOException ignore) {
        }
        hostNameView.setText(hostName);
        portView.setText(port);
        final Runnable writeConfig = () -> {
            String hostName1 = hostNameView.getText().toString();
            String port1 = portView.getText().toString();
            try (FileWriter fw = new FileWriter(new File(networkEngineToConfig), false)) {
                fw.write("NETE\n");
                fw.write(hostName1); fw.write("\n");
                fw.write(port1); fw.write("\n");
                setEngineOptions(true);
            } catch (IOException e) {
                DroidFishApp.toast(e.getMessage(), Toast.LENGTH_LONG);
            }
        };
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            writeConfig.run();
            reShowDialog(NETWORK_ENGINE_DIALOG);
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> reShowDialog(NETWORK_ENGINE_DIALOG));
        builder.setOnCancelListener(dialog -> reShowDialog(NETWORK_ENGINE_DIALOG));
        builder.setNeutralButton(R.string.delete, (dialog, which) -> reShowDialog(DELETE_NETWORK_ENGINE_DIALOG));

        final Dialog dialog = builder.create();
        portView.setOnKeyListener((v, keyCode, event) -> {
            if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                writeConfig.run();
                dialog.cancel();
                reShowDialog(NETWORK_ENGINE_DIALOG);
                return true;
            }
            return false;
        });
        return dialog;
    }

    private Dialog deleteNetworkEngineDialog() {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.delete_network_engine);
        String msg = networkEngineToConfig;
        if (msg.lastIndexOf('/') >= 0)
            msg = msg.substring(msg.lastIndexOf('/')+1);
        builder.setMessage(getString(R.string.network_engine) + ": " + msg);
        builder.setPositiveButton(R.string.yes, (dialog, id) -> {
            new File(networkEngineToConfig).delete();
            String engine = settings.getString("engine", "stockfish");
            if (engine.equals(networkEngineToConfig)) {
                engine = "stockfish";
                Editor editor = settings.edit();
                editor.putString("engine", engine);
                editor.apply();
                dialog.dismiss();
                setEngineOptions(false);
                setEngine(engine);
            }
            dialog.cancel();
            reShowDialog(NETWORK_ENGINE_DIALOG);
        });
        builder.setNegativeButton(R.string.no, (dialog, id) -> {
            dialog.cancel();
            reShowDialog(NETWORK_ENGINE_DIALOG);
        });
        builder.setOnCancelListener(dialog -> reShowDialog(NETWORK_ENGINE_DIALOG));
        return builder.create();
    }

    /** Open a load/save file dialog. Uses SAF on API 30+, OI file manager as fallback. */
    private void selectFile(int titleMsg, int buttonMsg, String settingsName, String defaultDir,
                            int dialog, int result) {
        setAutoMode(AutoMode.OFF);
        if (Build.VERSION.SDK_INT >= 30) {
            if (result == RESULT_OI_PGN_LOAD) {
                openPgnLauncher.launch(new String[]{"application/x-chess-pgn", "application/vnd.chess-pgn", "*/*"});
            } else if (result == RESULT_OI_FEN_LOAD) {
                openFenLauncher.launch(new String[]{"*/*"});
            } else if (result == RESULT_OI_PGN_SAVE) {
                createPgnLauncher.launch("game.pgn");
            } else {
                reShowDialog(dialog);
            }
            return;
        }
        String action = "org.openintents.action.PICK_FILE";
        Intent i = new Intent(action);
        String currentFile = settings.getString(settingsName, "");
        if (!currentFile.contains(File.separator))
            currentFile = new File(new File(StorageProvider.getBaseDir(), defaultDir), currentFile).getAbsolutePath();
        i.setData(Uri.fromFile(new File(currentFile)));
        i.putExtra("org.openintents.extra.TITLE", getString(titleMsg));
        i.putExtra("org.openintents.extra.BUTTON_TEXT", getString(buttonMsg));
        try {
            startActivityForResult(i, result);
        } catch (ActivityNotFoundException e) {
            reShowDialog(dialog);
        }
    }

    private boolean hasScidProvider() {
        try {
            getPackageManager().getPackageInfo("org.scid.android", 0);
            return true;
        } catch (NameNotFoundException ex) {
            return false;
        }
    }

    private void selectScidFile() {
        setAutoMode(AutoMode.OFF);
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("org.scid.android",
                                              "org.scid.android.SelectFileActivity"));
        intent.setAction(".si4");
        try {
            startActivityForResult(intent, RESULT_SELECT_SCID);
        } catch (ActivityNotFoundException e) {
            DroidFishApp.toast(e.getMessage(), Toast.LENGTH_LONG);
        }
    }

    public static boolean hasFenProvider(PackageManager manager) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT); 
        i.setType("application/x-chess-fen");
        List<ResolveInfo> resolvers = manager.queryIntentActivities(i, 0);
        return (resolvers != null) && (resolvers.size() > 0);
    }

    private void getFen() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT); 
        i.setType("application/x-chess-fen");
        try {
            startActivityForResult(i, RESULT_GET_FEN);
        } catch (ActivityNotFoundException e) {
            DroidFishApp.toast(e.getMessage(), Toast.LENGTH_LONG);
        }
    }

    final static int FT_NONE = 0;
    final static int FT_PGN  = 1;
    final static int FT_SCID = 2;
    final static int FT_FEN  = 3;

    private int currFileType() {
        return settings.getInt("currFT", FT_NONE);
    }

    /** Return path name for the last used PGN or SCID file. */
    private String currPathName() {
        int ft = settings.getInt("currFT", FT_NONE);
        switch (ft) {
        case FT_PGN: {
            String ret = settings.getString("currentPGNFile", "");
            if (!ret.contains(File.separator))
                ret = new File(StorageProvider.getPgnDir(), ret).getAbsolutePath();
            return ret;
        }
        case FT_SCID:
            return settings.getString("currentScidFile", "");
        case FT_FEN:
            return settings.getString("currentFENFile", "");
        default:
            return "";
        }
    }

    /** Save current game to a PGN file. */
    private void savePGNToFile(String pathName) {
        String pgn = ctrl.getPGN();
        String pgnToken = cache.storeString(pgn);
        Editor editor = settings.edit();
        editor.putString("currentPGNFile", pathName);
        editor.putInt("currFT", FT_PGN);
        editor.apply();
        Intent i = new Intent(DroidFish.this, EditPGNSave.class);
        i.setAction("org.petero.droidfish.saveFile");
        i.putExtra("org.petero.droidfish.pathname", pathName);
        i.putExtra("org.petero.droidfish.pgn", pgnToken);
        setEditPGNBackup(i, pathName);
        startActivityForResult(i, RESULT_SAVE_PGN);
    }

    /** Set a Boolean value in the Intent to decide if backups should be made
     *  when games in a PGN file are overwritten or deleted. */
    private void setEditPGNBackup(Intent i, String pathName) {
        boolean backup = storageAvailable() && !pathName.equals(getAutoSaveFile());
        i.putExtra("org.petero.droidfish.backup", backup);
    }

    /** Get the full path to the auto-save file. */
    private static String getAutoSaveFile() {
        return new File(StorageProvider.getPgnDir(), ".autosave.pgn").getAbsolutePath();
    }

    @Override
    public void autoSaveGameIfAllowed(String pgn) {
        if (storageAvailable())
            autoSaveGame(pgn);
    }

    /** Save a copy of the pgn data in the .autosave.pgn file. */
    public static void autoSaveGame(String pgn) {
        PGNFile pgnFile = new PGNFile(getAutoSaveFile());
        pgnFile.autoSave(pgn);
    }

    /** Load a PGN game from a file. */
    private void loadPGNFromFile(String pathName) {
        loadPGNFromFile(pathName, true);
    }

    /** Load a PGN game from a file. */
    private void loadPGNFromFile(String pathName, boolean updateCurrFile) {
        if (updateCurrFile) {
            Editor editor = settings.edit();
            editor.putString("currentPGNFile", pathName);
            editor.putInt("currFT", FT_PGN);
            editor.apply();
        }
        Intent i = new Intent(DroidFish.this, EditPGNLoad.class);
        i.setAction("org.petero.droidfish.loadFile");
        i.putExtra("org.petero.droidfish.pathname", pathName);
        i.putExtra("org.petero.droidfish.updateDefFilePos", updateCurrFile);
        setEditPGNBackup(i, pathName);
        startActivityForResult(i, RESULT_LOAD_PGN);
    }

    /** Load a FEN position from a file. */
    private void loadFENFromFile(String pathName) {
        if (pathName == null)
            return;
        Editor editor = settings.edit();
        editor.putString("currentFENFile", pathName);
        editor.putInt("currFT", FT_FEN);
        editor.apply();
        Intent i = new Intent(DroidFish.this, LoadFEN.class);
        i.setAction("org.petero.droidfish.loadFen");
        i.putExtra("org.petero.droidfish.pathname", pathName);
        startActivityForResult(i, RESULT_LOAD_FEN);
    }

    private void setFenHelper(String fen, boolean setModified) {
        if (fen == null)
            return;
        try {
            ctrl.setFENOrPGN(fen, setModified);
        } catch (ChessParseError e) {
            // If FEN corresponds to illegal chess position, go into edit board mode.
            try {
                TextIO.readFEN(fen);
            } catch (ChessParseError e2) {
                if (e2.pos != null)
                    startEditBoard(TextIO.toFEN(e2.pos));
            }
        }
    }

    @Override
    public void requestPromotePiece() {
        showDroidFishDialog(PROMOTE_DIALOG);
    }

    @Override
    public void reportInvalidMove(Move m) {
        String msg = String.format(Locale.US, "%s %s-%s",
                                   getString(R.string.invalid_move),
                                   TextIO.squareToString(m.from), TextIO.squareToString(m.to));
        DroidFishApp.toast(msg, Toast.LENGTH_SHORT);
    }

    @Override
    public void reportEngineName(String engine) {
        String msg = String.format(Locale.US, "%s: %s",
                                   getString(R.string.engine), engine);
        DroidFishApp.toast(msg, Toast.LENGTH_SHORT);
    }

    @Override
    public void reportEngineError(String errMsg) {
        String msg = String.format(Locale.US, "%s: %s",
                                   getString(R.string.engine_error), errMsg);
        runOnUiThread(() -> {
            new MaterialAlertDialogBuilder(DroidFish.this)
                .setTitle(R.string.engine_error)
                .setMessage(errMsg)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        });
    }

    /** Initialize text to speech if enabled in settings. */
    private void initSpeech() {
        if (moveAnnounceType.startsWith("speech_"))
            speech.initialize(this, moveAnnounceType.substring(7));
    }

    @Override
    public void movePlayed(Position pos, Move move, boolean computerMove) {
        if (moveAnnounceType.startsWith("speech_")) {
            speech.say(pos, move, moveSoundEnabled && computerMove);
        } else if (moveSoundEnabled && computerMove) {
            if (moveSound != null)
                moveSound.release();
            try {
                moveSound = MediaPlayer.create(this, R.raw.movesound);
                if (moveSound != null)
                    moveSound.start();
            } catch (NotFoundException ignore) {
            }
        }
        if (vibrateEnabled && computerMove) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(500);
        }
    }

    @Override
    public void runOnUIThread(Runnable runnable) {
        runOnUiThread(runnable);
    }

    /** Decide if user should be warned about heavy CPU usage. */
    private void updateNotification() {
        boolean warn = false;
        if (lastVisibleMillis != 0) { // GUI not visible
            warn = lastComputationMillis >= lastVisibleMillis + 60000;
        }
        setNotification(warn);
    }

    private boolean notificationActive = false;
    private NotificationChannel notificationChannel = null;

    /** Set/clear the "heavy CPU usage" notification. */
    private void setNotification(boolean show) {
        if (notificationActive == show)
            return;
        notificationActive = show;

        final int cpuUsage = 1;
        Context context = getApplicationContext();
        NotificationManagerCompat notificationManagerCompat =
                NotificationManagerCompat.from(context);

        if (show) {
            final int sdkVer = Build.VERSION.SDK_INT;
            String channelId = "general";
            if (notificationChannel == null && sdkVer >= 26) {
                notificationChannel = new NotificationChannel(channelId, "General",
                                                              NotificationManager.IMPORTANCE_HIGH);
                NotificationManager notificationManager =
                        (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.createNotificationChannel(notificationChannel);
            }

            int icon = (sdkVer >= 21) ? R.drawable.silhouette : R.mipmap.ic_launcher;
            String tickerText = getString(R.string.heavy_cpu_usage);
            String contentTitle = getString(R.string.background_processing);
            String contentText = getString(R.string.lot_cpu_power);
            Intent notificationIntent = new Intent(this, CPUWarning.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

            Notification notification = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(icon)
                    .setTicker(tickerText)
                    .setOngoing(true)
                    .setContentTitle(contentTitle)
                    .setContentText(contentText)
                    .setContentIntent(contentIntent)
                    .build();
            notificationManagerCompat.notify(cpuUsage, notification);
        } else {
            notificationManagerCompat.cancel(cpuUsage);
        }
    }

    private String timeToString(int time) {
        int secs = (int)Math.floor((time + 999) / 1000.0);
        boolean neg = false;
        if (secs < 0) {
            neg = true;
            secs = -secs;
        }
        int mins = secs / 60;
        secs -= mins * 60;
        StringBuilder ret = new StringBuilder();
        if (neg) ret.append('-');
        ret.append(mins);
        ret.append(':');
        if (secs < 10) ret.append('0');
        ret.append(secs);
        return ret.toString();
    }

    private Handler handlerTimer = new Handler(Looper.getMainLooper());
    private Runnable r = () -> ctrl.updateRemainingTime();

    @Override
    public void setRemainingTime(int wTime, int bTime, int nextUpdate) {
        if (engineMatchController != null && engineMatchController.isRunning())
            return;
        if (ctrl.getGameMode().clocksActive()) {
            whiteTitleText.setText(getString(R.string.white_square_character) + " " + timeToString(wTime));
            blackTitleText.setText(getString(R.string.black_square_character) + " " + timeToString(bTime));
        } else {
            TreeMap<String,String> headers = new TreeMap<>();
            ctrl.getHeaders(headers);
            whiteTitleText.setText(headers.get("White"));
            blackTitleText.setText(headers.get("Black"));
        }
        handlerTimer.removeCallbacks(r);
        if (nextUpdate > 0)
            handlerTimer.postDelayed(r, nextUpdate);
    }

    private Handler autoModeTimer = new Handler(Looper.getMainLooper());
    private Runnable amRunnable = () -> {
        switch (autoMode) {
        case BACKWARD:
            ctrl.undoMove();
            setAutoMode(autoMode);
            break;
        case FORWARD:
            ctrl.redoMove();
            setAutoMode(autoMode);
            break;
        case OFF:
            break;
        }
    };

    /** Set automatic move forward/backward mode. */
    void setAutoMode(AutoMode am) {
        autoMode = am;
        switch (am) {
        case BACKWARD:
        case FORWARD:
            if (autoMoveDelay > 0)
                autoModeTimer.postDelayed(amRunnable, autoMoveDelay);
            break;
        case OFF:
            autoModeTimer.removeCallbacks(amRunnable);
            break;
        }
    }

    /** Disable automatic move mode if clocks are active. */
    void maybeAutoModeOff(GameMode gm) {
        if (gm.clocksActive())
            setAutoMode(AutoMode.OFF);
    }

    /** Go to given node in game tree. */
    public void goNode(Node node) {
        if (ctrl == null)
            return;

        // On android 4.1 this onClick method is called
        // even when you long click the move list. The test
        // below works around the problem.
        Dialog mlmd = moveListMenuDlg;
        if ((mlmd == null) || !mlmd.isShowing()) {
            setAutoMode(AutoMode.OFF);
            ctrl.goNode(node);
        }
    }
}
