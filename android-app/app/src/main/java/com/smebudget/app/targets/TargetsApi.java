package com.smebudget.app.targets;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

/** KPI targets, forecasts and alerts. The login token is added by ApiClient. */
public interface TargetsApi {

    @GET("api/targets")
    Call<List<TargetDto>> list();

    @GET("api/targets/{id}")
    Call<TargetDto> get(@Path("id") String id);

    @POST("api/targets")
    Call<TargetDto> create(@Body TargetBody body);

    @PUT("api/targets/{id}")
    Call<TargetDto> update(@Path("id") String id, @Body TargetBody body);

    @DELETE("api/targets/{id}")
    Call<Void> delete(@Path("id") String id);

    @GET("api/insights/forecasts")
    Call<List<ForecastDto>> forecasts();

    @POST("api/insights/forecasts")
    Call<List<ForecastDto>> runForecast();

    @GET("api/insights/alerts")
    Call<List<AlertDto>> alerts();

    @GET("api/reports/meeting")
    Call<MeetingReportDto> meetingReport();
}
