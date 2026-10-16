package com.smebudget.api.submissions;

/** One income-statement category the form can offer. */
public record CategoryResponse(String categoryCode, String displayName, int sortOrder, boolean teamManaged) {
}
