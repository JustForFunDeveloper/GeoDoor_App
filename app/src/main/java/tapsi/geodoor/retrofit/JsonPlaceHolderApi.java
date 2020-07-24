package tapsi.geodoor.retrofit;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import tapsi.geodoor.retrofit.models.AnswerModel;
import tapsi.geodoor.retrofit.models.AuthModel;
import tapsi.geodoor.retrofit.models.DeviceModel;

public interface JsonPlaceHolderApi {
    @POST("register")
    Call<AnswerModel> registerUser(@Body AuthModel model);

    @POST("login")
    Call<AnswerModel> loginUser(@Body AuthModel model);

    @POST("open")
    Call<AnswerModel> openDevice(@Body DeviceModel model);
}