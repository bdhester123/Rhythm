/*
 * Copyright (C) 2016 Actinarium
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.actinarium.rhythm.sample;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.MultiAutoCompleteTextView;
import com.actinarium.aligned.Utils;
import com.actinarium.rhythm.RhythmDrawable;
import com.actinarium.rhythm.RhythmOverlay;
import com.actinarium.rhythm.common.RhythmInflationException;
import com.actinarium.rhythm.config.RhythmOverlayInflater;
import com.actinarium.rhythm.control.RhythmFrameLayout;
import com.actinarium.rhythm.sample.util.ViewUtils;
import com.actinarium.rhythm.spec.DimensionsLabel;
import com.actinarium.rhythm.spec.Fill;
import com.actinarium.rhythm.spec.GridLines;
import com.actinarium.rhythm.spec.InsetGroup;
import com.actinarium.rhythm.spec.Keyline;
import com.actinarium.rhythm.spec.RatioKeyline;

import java.util.ArrayList;

/**
 * A presenter for the Rhythm Sandbox card, where you can try the configuration at runtime
 *
 * @author Paul Danyliuk
 */
public class RhythmSandbox {

    /*
     * Words for auto-complete. Not containing words from custom layers
     */
    String[] ALL_CONFIG_WORDS = {
            Keyline.Factory.LAYER_TYPE, GridLines.Factory.LAYER_TYPE, Fill.Factory.LAYER_TYPE,
            InsetGroup.Factory.LAYER_TYPE, RatioKeyline.Factory.LAYER_TYPE, DimensionsLabel.Factory.LAYER_TYPE,
            "outside", "no-clip", "clip-only",
            "from=", "distance=", "step=", "ratio=", "gravity=",
            "top", "bottom", "left", "right",
            "top=", "bottom=", "left=", "right=", "width=", "height=",
            "color=", "color=#",
            "limit=", "offset=", "thickness=", "text-color=", "text-size="
    };

    private static final String DEFAULT_SANDBOX_CONFIG =
            "@margin=16dp\n" +
            "@grid_color=#800091EA\n" +
            "grid-lines step=8dp from=left color=@grid_color\n" +
            "grid-lines step=4dp from=top color=@grid_color\n" +
            "inset left=0dp width=@margin\n" +
            " fill\n" +
            "inset right=0dp width=@margin\n" +
            " fill\n" +
            "keyline distance=@margin from=left\n" +
            "keyline distance=@margin from=right\n";

    // -----------------------------------------------------------------------------------------------------------------

    private static final String ARG_RENDER = "com.actinarium.rhythm.sample.arg.RENDER";

    private AppCompatActivity mActivity;
    private RhythmOverlayInflater mOverlayInflater;

    private MultiAutoCompleteTextView mOverlayConfig;
    private RhythmFrameLayout mPreview;
    private boolean mDoRender;

    /**
     * Initialize a presenter for sandbox
     *
     * @param activity        Activity that hosts this sandbox
     * @param rootView        Root view of the sandbox
     * @param overlayInflater Overlay inflater used to inflate rhythm config
     */
    public RhythmSandbox(AppCompatActivity activity, View rootView, RhythmOverlayInflater overlayInflater) {
        mActivity = activity;
        mOverlayInflater = overlayInflater;

        // Find and init preview layout
        mPreview = (RhythmFrameLayout) rootView.findViewById(R.id.preview);
        mPreview.setRhythmDrawable(new RhythmDrawable(null));

        // Find and init overlay config text box
        mOverlayConfig = (MultiAutoCompleteTextView) rootView.findViewById(R.id.config);
        mOverlayConfig.setHorizontallyScrolling(true);

        // Fix config text box metrics
        int i4dp = activity.getResources().getDimensionPixelOffset(R.dimen.i4dp);
        Utils.setExactMetrics(mOverlayConfig, i4dp * 6, i4dp * 5, i4dp * 3);

        // Enable auto-complete for config
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line, ALL_CONFIG_WORDS);
        mOverlayConfig.setTokenizer(new ConfigTokenizer());
        mOverlayConfig.setAdapter(adapter);

        // Find and init Apply button
        final Button applyButton = (Button) rootView.findViewById(R.id.apply);
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updatePreview();
            }
        });
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(ARG_RENDER, mDoRender);
    }

    public void onRestoreInstanceState(Bundle savedState) {
        if (savedState == null) {
            mOverlayConfig.setText(DEFAULT_SANDBOX_CONFIG);
        } else {
            mDoRender = savedState.getBoolean(ARG_RENDER, false);
            if (mDoRender) {
                updatePreview();
            }
        }
    }

    /**
     * Take the text out of text field and render an overlay into preview frame
     */
    private void updatePreview() {
        String overlayConfig = mOverlayConfig.getText().toString();

        if (validate(overlayConfig)) {
            mDoRender = true;
            final RhythmOverlay overlay = mOverlayInflater.inflateOverlay(overlayConfig);
            mPreview.getRhythmDrawable().setOverlay(overlay);
        }
    }

    /**
     * Perform overlay validation, show dialog if issues encountered
     *
     * @param overlayConfig Overlay config string to validate
     * @return true if validation passed
     */
    private boolean validate(String overlayConfig) {
        // If config is empty, short-circuit
        if (overlayConfig.trim().length() == 0) {
            String[] errorArray = new String[]{(mActivity.getString(R.string.validation_config_empty))};
            InvalidOverlayDialogFragment dialogFragment = InvalidOverlayDialogFragment.newInstance(errorArray);
            dialogFragment.show(mActivity.getSupportFragmentManager(), InvalidOverlayDialogFragment.TAG);
            return false;
        }

        // Otherwise verify line by line and collect errors
        ArrayList<String> errors = new ArrayList<>();

        /*
        String[] lines = overlayConfig.split("\\n");
        List<String> singletonList = new ArrayList<>(1);
        singletonList.add(null);
        for (int i = 0, linesLength = lines.length; i < linesLength; i++) {
            singletonList.set(0, lines[i]);
            try {
                mOverlayInflater.inflateOverlay(singletonList);
            } catch (RhythmInflationException e) {
                errors.add(mActivity.getString(R.string.validation_config_line, i + 1, e.getMessage()));
            } catch (Exception e) {
                errors.add(mActivity.getString(R.string.validation_config_line_unexpected, i + 1, e.getMessage()));
            }
        } */

        try {
            mOverlayInflater.inflateOverlay(overlayConfig);
        } catch (RhythmInflationException e) {
            errors.add(mActivity.getString(R.string.validation_config_line, 0, e.getMessage()));
        } catch (Exception e) {
            errors.add(mActivity.getString(R.string.validation_config_line_unexpected, 0, e.getMessage()));
        }

        final int errorNumber = errors.size();
        if (errorNumber == 0) {
            return true;
        } else {
            String[] errorArray = new String[errorNumber];
            errors.toArray(errorArray);
            InvalidOverlayDialogFragment dialogFragment = InvalidOverlayDialogFragment.newInstance(errorArray);
            dialogFragment.show(mActivity.getSupportFragmentManager(), InvalidOverlayDialogFragment.TAG);
            return false;
        }
    }

    /**
     * Imperfect tokenizer for configuration auto-complete
     */
    private static class ConfigTokenizer implements MultiAutoCompleteTextView.Tokenizer {
        @Override
        public int findTokenStart(CharSequence text, int cursor) {
            int i = cursor;
            while (i > 0 && text.charAt(i - 1) != ' ' && text.charAt(i - 1) != '=' && text.charAt(i - 1) != '\n') {
                i--;
            }
            return i;
        }

        @Override
        public int findTokenEnd(CharSequence text, int cursor) {
            int i = cursor;
            int len = text.length();
            while (i < len) {
                if (text.charAt(i) == ' ' || text.charAt(i) == '=' || text.charAt(i) == '\n') {
                    return i;
                } else {
                    i++;
                }
            }
            return len;
        }

        @Override
        public CharSequence terminateToken(CharSequence text) {
            int i = text.length();
            if (i > 0 && text.charAt(i - 1) == ' ' || text.charAt(i - 1) == '=' || text.charAt(i - 1) == '\n' || text.charAt(i - 1) == '#') {
                return text;
            } else {
                return text + " ";
            }
        }
    }

    /**
     * Dialog for validation errors
     */
    public static class InvalidOverlayDialogFragment extends DialogFragment {

        public static final String TAG = "InvalidOverlayDialogFragment";

        public static final String ARG_ERRORS = "com.actinarium.rhythm.sample.intent.arg.ERRORS";

        private Context mContext;

        public static InvalidOverlayDialogFragment newInstance(String[] errors) {
            InvalidOverlayDialogFragment fragment = new InvalidOverlayDialogFragment();
            Bundle args = new Bundle(1);
            args.putStringArray(ARG_ERRORS, errors);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            mContext = activity;
        }

        @Override
        @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final String[] errors = getArguments().getStringArray(ARG_ERRORS);

            final int bulletRadius = getResources().getDimensionPixelSize(R.dimen.bulletRadius);
            final int bulletCenterX = getResources().getDimensionPixelSize(R.dimen.bulletCenterX);
            final int leadingMargin = getResources().getDimensionPixelSize(R.dimen.bulletLeadingMargin);
            CharSequence message = ViewUtils.makeBulletList(bulletRadius, bulletCenterX, leadingMargin, errors);

            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder
                    .setTitle(R.string.validation_config_dialog_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
            return builder.create();
        }
    }
}
