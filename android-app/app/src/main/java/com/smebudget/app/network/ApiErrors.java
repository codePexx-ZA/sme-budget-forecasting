package com.smebudget.app.network;

import com.google.gson.Gson;

import okhttp3.ResponseBody;
import retrofit2.Response;

/** Reads the {"message": "..."} error body that every API endpoint returns, to show it to the user. */
public final class ApiErrors {

    private static final Gson GSON = new Gson();

    private ApiErrors() {
    }

    /** Returns the server's message, or {@code fallback} if the response has none. */
    public static String message(Response<?> response, String fallback) {
        ResponseBody body = response.errorBody();
        if (body == null) {
            return fallback;
        }
        try {
            ErrorBody parsed = GSON.fromJson(body.charStream(), ErrorBody.class);
            if (parsed != null && parsed.message != null && !parsed.message.isEmpty()) {
                return parsed.message;
            }
        } catch (Exception e) {
            // Not our JSON error format (e.g. a proxy error page); use the fallback instead.
        } finally {
            body.close();
        }
        return fallback;
    }

    private static final class ErrorBody {
        String message;
    }
}
