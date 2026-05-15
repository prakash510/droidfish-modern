/*
    DroidFish - An Android chess program.
    Copyright (C) 2024  Peter Osterlund, peterosterlund2@gmail.com

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

package org.petero.droidfish.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * A vertical evaluation bar that displays engine evaluation as a filled bar.
 * White fills from the bottom, black fills from the top.
 * Uses the same sigmoid function as lichess to convert centipawn scores to fill percentage.
 */
public class EvalBarView extends View {

    private static final int COLOR_WHITE = Color.parseColor("#FFFFFF");
    private static final int COLOR_BLACK = Color.parseColor("#303030");
    private static final int COLOR_CENTER_LINE = Color.parseColor("#808080");
    private static final long ANIMATION_DURATION_MS = 300;

    private final Paint whitePaint;
    private final Paint blackPaint;
    private final Paint centerLinePaint;

    /** Current animated fill ratio [0, 1] where 1.0 = full white, 0.0 = full black. */
    private float fillRatio = 0.5f;

    private ValueAnimator animator;

    public EvalBarView(Context context) {
        this(context, null);
    }

    public EvalBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EvalBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePaint.setColor(COLOR_WHITE);
        whitePaint.setStyle(Paint.Style.FILL);

        blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPaint.setColor(COLOR_BLACK);
        blackPaint.setStyle(Paint.Style.FILL);

        centerLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerLinePaint.setColor(COLOR_CENTER_LINE);
        centerLinePaint.setStyle(Paint.Style.FILL);
        centerLinePaint.setStrokeWidth(1f);
    }

    /**
     * Set the evaluation score and animate the bar to the new position.
     *
     * @param score     The evaluation score in centipawns from WHITE's perspective.
     * @param isMate    True if the score represents a mate-in-N (score = number of moves).
     */
    public void setScore(int score, boolean isMate) {
        float targetFill = computeFillRatio(score, isMate);
        animateToFill(targetFill);
    }

    /** Reset the bar to the neutral (equal) position without animation. */
    public void resetToNeutral() {
        if (animator != null) {
            animator.cancel();
        }
        fillRatio = 0.5f;
        invalidate();
    }

    /** Maximum centipawn value that fills the bar completely. */
    private static final float MAX_CP = 500f;

    /**
     * Compute fill ratio using linear scaling capped at ±5 pawns.
     * Fill = 0.5 + (cp / (2 * MAX_CP)), clamped to [0, 1].
     * Mate scores always fill completely.
     */
    private float computeFillRatio(int score, boolean isMate) {
        if (isMate) {
            return score > 0 ? 1.0f : 0.0f;
        }

        float clamped = Math.max(-MAX_CP, Math.min(MAX_CP, score));
        return 0.5f + clamped / (2.0f * MAX_CP);
    }

    private void animateToFill(float targetFill) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(fillRatio, targetFill);
        animator.setDuration(ANIMATION_DURATION_MS);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            fillRatio = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) {
            return;
        }

        // White fills from bottom. fillRatio=1.0 means all white (white from bottom to top).
        // The black portion is at the top, white portion at the bottom.
        float whiteHeight = height * fillRatio;
        float blackHeight = height - whiteHeight;

        // Draw black portion (top)
        canvas.drawRect(0, 0, width, blackHeight, blackPaint);

        // Draw white portion (bottom)
        canvas.drawRect(0, blackHeight, width, height, whitePaint);

        // Draw center line (at the 50% mark)
        float centerY = height * 0.5f;
        canvas.drawLine(0, centerY, width, centerY, centerLinePaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Prefer 14dp width, match parent height
        int desiredWidth = (int) (14 * getResources().getDisplayMetrics().density);
        int width = resolveSize(desiredWidth, widthMeasureSpec);
        int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }
}
