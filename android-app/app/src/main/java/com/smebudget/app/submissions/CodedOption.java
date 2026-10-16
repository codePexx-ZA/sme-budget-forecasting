package com.smebudget.app.submissions;

/** A dropdown row: the value sent to the API, and the words shown on screen. */
final class CodedOption {

    final String code;
    final String label;

    CodedOption(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
