package com.smebudget.app.submissions;

import android.view.View;
import android.widget.TextView;

import com.smebudget.app.R;

/**
 * Draws a status so the word is not the only signal: draft is dashed, waiting has a start bar,
 * approved has a thick bottom edge, rejected is a solid outline, and superseded is dotted.
 */
final class StatusChips {

    private StatusChips() {
    }

    static void apply(TextView view, String status) {
        int background;
        int color;
        int layer = View.LAYER_TYPE_NONE;
        if (SubmissionStatus.DRAFT.equals(status)) {
            background = R.drawable.bg_status_draft;
            color = R.color.status_draft_fg;
            layer = View.LAYER_TYPE_SOFTWARE;
        } else if (SubmissionStatus.SUBMITTED.equals(status)) {
            background = R.drawable.bg_status_waiting;
            color = R.color.status_waiting_fg;
        } else if (SubmissionStatus.APPROVED.equals(status)) {
            background = R.drawable.bg_status_approved;
            color = R.color.status_approved_fg;
        } else if (SubmissionStatus.REJECTED.equals(status)) {
            background = R.drawable.bg_status_rejected;
            color = R.color.status_rejected_fg;
        } else if (SubmissionStatus.SUPERSEDED.equals(status)) {
            background = R.drawable.bg_status_superseded;
            color = R.color.status_superseded_fg;
            layer = View.LAYER_TYPE_SOFTWARE;
        } else {
            background = R.drawable.bg_status_neutral;
            color = R.color.ink;
        }
        int padH = view.getResources().getDimensionPixelSize(R.dimen.chip_padding_h);
        int padV = view.getResources().getDimensionPixelSize(R.dimen.chip_padding_v);
        view.setLayerType(layer, null);
        view.setBackgroundResource(background);
        view.setTextColor(view.getResources().getColor(color, view.getContext().getTheme()));
        view.setPaddingRelative(padH, padV, padH, padV);
    }
}
