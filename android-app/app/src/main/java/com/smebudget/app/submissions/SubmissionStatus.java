package com.smebudget.app.submissions;

/** Names the lowercase budget statuses stored on a submission. */
final class SubmissionStatus {

    static final String DRAFT = "draft";
    static final String SUBMITTED = "submitted";
    static final String APPROVED = "approved";
    static final String REJECTED = "rejected";
    static final String SUPERSEDED = "superseded";

    private SubmissionStatus() {
    }

    /** A submitter can still change a draft or a budget the approver sent back. */
    static boolean editable(String status) {
        return DRAFT.equals(status) || REJECTED.equals(status);
    }
}
