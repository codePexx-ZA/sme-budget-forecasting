package com.smebudget.app.submissions;

/** Comment sent with approve (optional) or reject (required). */
public class DecisionBody {

    final String comments;

    public DecisionBody(String comments) {
        this.comments = comments;
    }
}
