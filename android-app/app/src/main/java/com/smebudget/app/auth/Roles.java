package com.smebudget.app.auth;

import android.content.Context;

import com.smebudget.app.R;

/** The three role values from the database (app_user.role) and their display names. */
public final class Roles {

    public static final String SUBMITTER = "submitter";
    public static final String APPROVER = "approver";
    public static final String DECISION_MAKER = "decision_maker";

    private Roles() {
    }

    /** Turns a role value such as "decision_maker" into "Decision Maker" for the screen. */
    public static String label(Context context, String role) {
        if (SUBMITTER.equals(role)) {
            return context.getString(R.string.role_submitter);
        } else if (APPROVER.equals(role)) {
            return context.getString(R.string.role_approver);
        } else if (DECISION_MAKER.equals(role)) {
            return context.getString(R.string.role_decision_maker);
        }
        return role == null ? "" : role;
    }
}
