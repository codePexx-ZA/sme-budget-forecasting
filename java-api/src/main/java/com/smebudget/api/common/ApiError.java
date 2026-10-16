package com.smebudget.api.common;

/** Error body every endpoint returns, so the app can show {@code message} to the user. */
public record ApiError(String message) {
}
