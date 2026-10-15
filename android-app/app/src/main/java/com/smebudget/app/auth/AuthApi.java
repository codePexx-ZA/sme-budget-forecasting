package com.smebudget.app.auth;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/** Login endpoints of the Spring Boot API (java-api, package auth). */
public interface AuthApi {

    @POST("api/auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("api/auth/logout")
    Call<Void> logout();

    @GET("api/auth/me")
    Call<UserDto> me();
}
