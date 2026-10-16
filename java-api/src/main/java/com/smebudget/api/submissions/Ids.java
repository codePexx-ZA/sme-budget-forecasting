package com.smebudget.api.submissions;

import java.util.UUID;

/** 32-character ids, the same shape as database/init (a UUID without dashes). */
final class Ids {

    private Ids() {
    }

    static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
