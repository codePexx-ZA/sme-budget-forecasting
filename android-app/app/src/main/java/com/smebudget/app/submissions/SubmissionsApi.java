package com.smebudget.app.submissions;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

/** Budget submission endpoints of the Spring Boot API (java-api, package submissions). */
public interface SubmissionsApi {

    @GET("api/submissions/categories")
    Call<List<CategoryDto>> categories();

    /** This business's year-end, from the business row the meeting report already reads. */
    @GET("api/reports/financial-year")
    Call<FinancialYearDto> financialYear();

    @GET("api/submissions")
    Call<List<SubmissionSummary>> list();

    @GET("api/submissions/{id}")
    Call<SubmissionDetail> get(@Path("id") String id);

    @POST("api/submissions")
    Call<SubmissionDetail> create(@Body SubmissionBody body);

    @PUT("api/submissions/{id}")
    Call<SubmissionDetail> update(@Path("id") String id, @Body SubmissionBody body);

    @DELETE("api/submissions/{id}")
    Call<MessageDto> delete(@Path("id") String id);

    @POST("api/submissions/{id}/submit")
    Call<SubmissionDetail> submit(@Path("id") String id);

    @POST("api/submissions/{id}/approve")
    Call<SubmissionDetail> approve(@Path("id") String id, @Body DecisionBody body);

    @POST("api/submissions/{id}/reject")
    Call<SubmissionDetail> reject(@Path("id") String id, @Body DecisionBody body);
}
