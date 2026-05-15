/*
    DroidFish - An Android chess program.
    Copyright (C) 2020  Peter Österlund, peterosterlund2@gmail.com

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

package org.petero.droidfish.activities.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import org.petero.droidfish.StorageProvider;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.petero.droidfish.FileUtil;
import org.petero.droidfish.R;
import org.petero.droidfish.activities.Preferences;

import java.io.File;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


/** A text preference representing a file or directory, with a corresponding browse button. */
public class EditFilePreference extends EditTextPreference {
    private boolean pickDirectory = false; // True to pick a directory, false to pick a file
    private String defaultPath = "";   // Default path when current value does not define a path
    private String ignorePattern = ""; // Regexp for values to be treated as non-paths
    private View view;

    public EditFilePreference(Context context) {
        super(context);
        init(null);
    }

    public EditFilePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public EditFilePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        if (attrs != null) {
            pickDirectory = attrs.getAttributeBooleanValue(null, "pickDirectory", false);
            defaultPath = getStringValue(attrs, "defaultPath");
            ignorePattern = getStringValue(attrs, "ignorePattern");
        }
    }

    private static String getStringValue(AttributeSet attrs, String name) {
        String val = attrs.getAttributeValue(null, name);
        return val == null ? "" : val;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        this.view = view;
        addBrowseButton();
    }

    private void addBrowseButton() {
        if (view == null)
            return;

        LinearLayout widgetFrameView = view.findViewById(android.R.id.widget_frame);
        if (widgetFrameView == null)
            return;
        widgetFrameView.setVisibility(View.VISIBLE);
        int count = widgetFrameView.getChildCount();
        if (count > 0)
            widgetFrameView.removeViews(0, count);

        ImageView button = new ImageView(getContext());
        widgetFrameView.addView(button);
        widgetFrameView.setMinimumWidth(0);

        boolean hasBrowser = FileBrowseUtil.hasBrowser(getContext().getPackageManager(),
                                                       pickDirectory);
        FileBrowseUtil.setBrowseImage(getContext().getResources(), button, hasBrowser);
        button.setOnClickListener(view -> browseFile());
    }

    private void browseFile() {
        String currentPath = getText();
        if (matchPattern(currentPath))
            currentPath = "";
        if (currentPath.isEmpty() || !currentPath.contains(File.separator)) {
            String basePath = new File(StorageProvider.getBaseDir(), defaultPath.replace("DroidFish/", "")).getAbsolutePath();
            if (!currentPath.isEmpty())
                basePath = basePath + File.separator + currentPath;
            currentPath = basePath;
        }

        Context context = getContext();
        if (!(context instanceof Preferences))
            return;
        Preferences prefs = (Preferences) context;

        if (Build.VERSION.SDK_INT >= 30) {
            Intent i;
            if (pickDirectory) {
                i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            } else {
                i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
            }
            try {
                prefs.runActivity(i, (resultCode, data) -> {
                    if (resultCode == Activity.RESULT_OK && data != null) {
                        Uri uri = data.getData();
                        if (uri != null) {
                            if (pickDirectory) {
                                prefs.getContentResolver().takePersistableUriPermission(uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                setText(uri.toString());
                            } else {
                                String pathName = FileUtil.getFilePathFromUri(uri);
                                if (pathName != null)
                                    setText(pathName);
                            }
                        }
                    }
                });
            } catch (ActivityNotFoundException ignore) {
            }
            return;
        }

        String title = getContext().getString(pickDirectory ? R.string.select_directory
                                                            : R.string.select_file);
        Intent i = new Intent(FileBrowseUtil.getPickAction(pickDirectory));
        i.setData(Uri.fromFile(new File(currentPath)));
        i.putExtra("org.openintents.extra.TITLE", title);
        try {
            prefs.runActivity(i, (resultCode, data) -> {
                if (resultCode == Activity.RESULT_OK) {
                    String pathName = FileUtil.getFilePathFromUri(data.getData());
                    if (pathName != null)
                        setText(pathName);
                }
            });
        } catch (ActivityNotFoundException ignore) {
        }
    }

    private boolean matchPattern(String s) {
        if (ignorePattern.isEmpty())
            return false;
        try {
            Pattern p = Pattern.compile(ignorePattern);
            return p.matcher(s).find();
        } catch (PatternSyntaxException ex) {
            return false;
        }
    }
}
