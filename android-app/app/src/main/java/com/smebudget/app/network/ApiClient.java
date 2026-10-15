package com.smebudget.app.network;

import android.content.Context;

import com.smebudget.app.BuildConfig;
import com.smebudget.app.auth.SessionManager;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * One shared Retrofit client for the Spring Boot API (owned by Dean).
 *
 * Every request automatically gets "Authorization: Bearer &lt;token&gt;" while the user is logged in,
 * so feature code only needs: {@code ApiClient.create(context, MyApi.class)}.
 */
public final class ApiClient {

    private static Retrofit retrofit;

    private ApiClient() {
    }

    public static <T> T create(Context context, Class<T> service) {
        return get(context).create(service);
    }

    private static synchronized Retrofit get(Context context) {
        if (retrofit == null) {
            SessionManager session = new SessionManager(context);

            // Logs only the method, URL and status code (never request bodies, so passwords stay out of Logcat).
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(BuildConfig.DEBUG ? HttpLoggingInterceptor.Level.BASIC : HttpLoggingInterceptor.Level.NONE);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request request = chain.request();
                        String token = session.getToken();
                        if (token != null) {
                            request = request.newBuilder().header("Authorization", "Bearer " + token).build();
                        }
                        return chain.proceed(request);
                    })
                    .addInterceptor(logging)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BuildConfig.API_BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
