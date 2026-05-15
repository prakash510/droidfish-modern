/*
    DroidFish - An Android chess program.
    Copyright (C) 2014  Peter Österlund, peterosterlund2@gmail.com

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

package org.petero.droidfish.activities;

import android.content.ActivityNotFoundException;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;

import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.petero.droidfish.DroidFishApp;
import org.petero.droidfish.FileUtil;
import org.petero.droidfish.R;
import org.petero.droidfish.Util;
import org.petero.droidfish.activities.util.FileBrowseUtil;
import org.petero.droidfish.engine.UCIOptions;

import java.io.File;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Edit UCI options.
 */
public class EditOptions extends AppCompatActivity {
    private UCIOptions uciOpts = null;
    private String engineName = "";
    private String workDir = "";
    private boolean hasBrowser = false; // True if OI file manager available

    private UCIOptions.StringOption currentStringOption; // Option that triggered file browsing
    private EditText currentTextField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupBackHandler();

        Intent i = getIntent();
        if (Build.VERSION.SDK_INT >= 33) {
            uciOpts = i.getSerializableExtra("org.petero.droidfish.ucioptions", UCIOptions.class);
        } else {
            uciOpts = (UCIOptions) i.getSerializableExtra("org.petero.droidfish.ucioptions");
        }
        engineName = i.getStringExtra("org.petero.droidfish.enginename");
        workDir = i.getStringExtra("org.petero.droidfish.workDir");
        hasBrowser = i.getBooleanExtra("org.petero.droidfish.localEngine", false);
        if (uciOpts != null) {
            if (hasBrowser)
                hasBrowser = FileBrowseUtil.hasBrowser(getPackageManager(), false);
            initUI();
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(DroidFishApp.setLanguage(newBase, false));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        initUI();
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                sendBackResult();
            }
        });
    }

    private void initUI() {
        String title = getString(R.string.edit_options_title);
        if (engineName != null)
            title = title + ": " + engineName;
        setTitle(title);

        setContentView(R.layout.editoptions);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        Util.setFullScreenMode(this, settings);

        LinearLayout content = findViewById(R.id.eo_content);

        if (uciOpts != null) {
            for (String name : uciOpts.getOptionNames()) {
                UCIOptions.OptionBase o = uciOpts.getOption(name);
                if (o.visible) {
                    View v = getViewForOption(o);
                    if (v != null)
                        content.addView(v);
                }
            }
        }

        Util.overrideViewAttribs(content);

        findViewById(R.id.eo_ok).setOnClickListener(v -> sendBackResult());
        findViewById(R.id.eo_cancel).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        findViewById(R.id.eo_reset).setOnClickListener(v -> {
            if (uciOpts != null) {
                boolean modified = false;
                for (String name : uciOpts.getOptionNames()) {
                    UCIOptions.OptionBase o = uciOpts.getOption(name);
                    if (!o.visible)
                        continue;
                    switch (o.type) {
                    case CHECK: {
                        UCIOptions.CheckOption co = (UCIOptions.CheckOption) o;
                        modified |= co.set(co.defaultValue);
                        break;
                    }
                    case SPIN: {
                        UCIOptions.SpinOption so = (UCIOptions.SpinOption) o;
                        modified |= so.set(so.defaultValue);
                        break;
                    }
                    case COMBO: {
                        UCIOptions.ComboOption co = (UCIOptions.ComboOption) o;
                        modified |= co.set(co.defaultValue);
                        break;
                    }
                    case STRING: {
                        UCIOptions.StringOption so = (UCIOptions.StringOption) o;
                        modified |=  so.set(so.defaultValue);
                        break;
                    }
                    case BUTTON:
                        break;
                    }
                }
                if (modified)
                    initUI();
            }
        });
    }

    private View getViewForOption(UCIOptions.OptionBase o) {
        switch (o.type) {
        case CHECK: {
            CheckBox cb = (CheckBox) getLayoutInflater().inflate(R.layout.uci_option_check, null);
            cb.setText(o.name);
            final UCIOptions.CheckOption co = (UCIOptions.CheckOption) o;
            cb.setChecked(co.value);
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> co.set(isChecked));
            return cb;
        }
        case SPIN: {
            View root = getLayoutInflater().inflate(R.layout.uci_option_spin, null);
            final UCIOptions.SpinOption so = (UCIOptions.SpinOption) o;
            String labelText = String.format(Locale.US, "%s (%d\u2013%d)", so.name, so.minValue, so.maxValue);
            ((TextView) root.findViewById(R.id.eo_label)).setText(labelText);
            EditText valueField = root.findViewById(R.id.eo_value);
            valueField.setText(so.getStringValue());
            if (so.minValue >= 0)
                valueField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            valueField.addTextChangedListener(new TextWatcher() {
                public void onTextChanged(CharSequence s, int start, int before, int count) { }
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override
                public void afterTextChanged(Editable s) {
                    try {
                        int newVal = Integer.parseInt(s.toString());
                        if (newVal < so.minValue)
                            so.set(so.minValue);
                        else if (newVal > so.maxValue)
                            so.set(so.maxValue);
                        else
                            so.set(newVal);
                    } catch (NumberFormatException ignore) {
                    }
                }
            });
            return root;
        }
        case COMBO: {
            View root = getLayoutInflater().inflate(R.layout.uci_option_combo, null);
            ((TextView) root.findViewById(R.id.eo_label)).setText(o.name);
            final UCIOptions.ComboOption co = (UCIOptions.ComboOption) o;
            Spinner spinner = root.findViewById(R.id.eo_value);
            ArrayAdapter<CharSequence> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, co.allowedValues);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            spinner.setSelection(adapter.getPosition(co.value));
            spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> av, View view, int position, long id) {
                    if ((position >= 0) && (position < co.allowedValues.length))
                        co.set(co.allowedValues[position]);
                }
                public void onNothingSelected(AdapterView<?> arg0) { }
            });
            return root;
        }
        case BUTTON: {
            ToggleButton tb = (ToggleButton) getLayoutInflater().inflate(R.layout.uci_option_button, null);
            final UCIOptions.ButtonOption bo = (UCIOptions.ButtonOption) o;
            bo.trigger = false;
            tb.setText(o.name);
            tb.setTextOn(o.name);
            tb.setTextOff(o.name);
            tb.setOnCheckedChangeListener((buttonView, isChecked) -> bo.trigger = isChecked);
            return tb;
        }
        case STRING: {
            View root = getLayoutInflater().inflate(R.layout.uci_option_string, null);
            ((TextView) root.findViewById(R.id.eo_label)).setText(String.format("%s ", o.name));
            final UCIOptions.StringOption so = (UCIOptions.StringOption) o;
            EditText valueField = root.findViewById(R.id.eo_value);
            valueField.setText(so.value);
            valueField.addTextChangedListener(new TextWatcher() {
                public void onTextChanged(CharSequence s, int start, int before, int count) { }
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override
                public void afterTextChanged(Editable s) {
                    so.set(s.toString());
                }
            });
            ImageButton browseBtn = root.findViewById(R.id.eo_browse);
            boolean isFileOption = hasBrowser && (o.name.toLowerCase().contains("file") ||
                                                  o.name.toLowerCase().contains("path"));
            FileBrowseUtil.setBrowseImage(getResources(), browseBtn, isFileOption);
            browseBtn.setOnClickListener(view -> browseFile(so, valueField));
            return root;
        }
        default:
            return null;
        }
    }

    private void browseFile(UCIOptions.StringOption so, EditText textField) {
        String currentFile = so.getStringValue();
        String sep = File.separator;
        if (!currentFile.contains(sep))
            currentFile = workDir + sep + currentFile;
        Intent i = new Intent(FileBrowseUtil.getPickAction(false));
        i.setData(Uri.fromFile(new File(currentFile)));
        i.putExtra("org.openintents.extra.TITLE", getString(R.string.select_file));
        try {
            startActivityForResult(i, RESULT_OI_SELECT_FILE);
            currentStringOption = so;
            currentTextField = textField;
        } catch (ActivityNotFoundException ignore) {
        }
    }

    static private final int RESULT_OI_SELECT_FILE = 0;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case RESULT_OI_SELECT_FILE:
            if (resultCode == RESULT_OK && currentStringOption != null) {
                String pathName = FileUtil.getFilePathFromUri(data.getData());
                if (pathName != null && currentTextField != null) {
                    if (currentStringOption.set(pathName))
                        currentTextField.setText(pathName);
                }
            }
            currentStringOption = null;
            currentTextField = null;
            break;
        }
    }

    private void sendBackResult() {
        if (uciOpts != null) {
            TreeMap<String, String> uciMap = new TreeMap<>();
            for (String name : uciOpts.getOptionNames()) {
                UCIOptions.OptionBase o = uciOpts.getOption(name);
                if (o != null) {
                    if (o instanceof UCIOptions.ButtonOption) {
                        UCIOptions.ButtonOption bo = (UCIOptions.ButtonOption) o;
                        if (bo.trigger)
                            uciMap.put(name, "");
                    } else {
                        uciMap.put(name, o.getStringValue());
                    }
                }
            }
            Intent i = new Intent();
            i.putExtra("org.petero.droidfish.ucioptions", uciMap);
            setResult(RESULT_OK, i);
            finish();
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }
}
