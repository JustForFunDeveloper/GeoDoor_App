package tapsi.geodoor.retrofit;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.lang.annotation.Annotation;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import tapsi.geodoor.database.tables.Config;
import tapsi.geodoor.retrofit.models.AnswerModel;
import tapsi.geodoor.retrofit.models.AuthModel;
import tapsi.geodoor.retrofit.models.CommandItem;

public class RetrofitHandler {
    private static final String TAG = "tapsi.retrofit";

    private Config config = null;
    private Retrofit retrofit;
    private JsonPlaceHolderApi jsonPlaceHolderApi;
    private Context context;

    private RetrofitListener callBack;

    public Config getConfig() {
        return config;
    }

    public interface RetrofitListener {
        void loginSuccessful();
        void loginFailed(AnswerModel answerModel);
        void loginOnFailure(String message);
        void registerSuccessful(AnswerModel answerModel);
        void registerFailed(AnswerModel answerModel);
        void registerOnFailure(String message);
        void sendCommandSuccessful(AnswerModel answerModel);
        void sendCommandOnFailure(String message);
    }

    public void setOnRetrofitListener(RetrofitHandler.RetrofitListener callBack) {
        this.callBack = callBack;
    }

    public AnswerModel parseError(Response<?> response) {
        Converter<ResponseBody, AnswerModel> converter = retrofit
                        .responseBodyConverter(AnswerModel.class, new Annotation[0]);

        AnswerModel error;

        try {
            error = converter.convert(response.errorBody());
        } catch (IOException e) {
            return new AnswerModel();
        }

        return error;
    }

    public void initHandler(Config config, Context context) {
        this.config = config;
        this.context = context;

        try {
            retrofit = new Retrofit.Builder()
                    .baseUrl(config.getIpAddress())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            jsonPlaceHolderApi = retrofit.create(JsonPlaceHolderApi.class);
        } catch (Exception ex) {
            Log.e(TAG, ex.getMessage());
        }
    }

    public void registerUser() {
        if (retrofit == null)
            return;

        AuthModel auth = new AuthModel();
        auth.setName(config.getName());

        Call<AnswerModel> call = jsonPlaceHolderApi.registerUser(auth);

        call.enqueue(new Callback<AnswerModel>() {
            @Override
            public void onResponse(Call<AnswerModel> call, Response<AnswerModel> response) {
                if (response.isSuccessful()) {
                    callBack.registerSuccessful(response.body());
                }
                else {
                    callBack.registerFailed(response.body());
                }
            }

            @Override
            public void onFailure(Call<AnswerModel> call, Throwable t) {
                callBack.registerOnFailure(t.getMessage());
            }
        });
    }

    public void loginUser() {
        if (retrofit == null)
            return;

        AuthModel auth = new AuthModel();
        auth.setName(config.getName());
        auth.setMd5Hash(config.getMd5Hash());

        Call<AnswerModel> call = jsonPlaceHolderApi.loginUser(auth);

        call.enqueue(new Callback<AnswerModel>() {
            @Override
            public void onResponse(Call<AnswerModel> call, Response<AnswerModel> response) {
                if (response.isSuccessful()) {
                    callBack.loginSuccessful();
                }
                else {
                    callBack.loginFailed(parseError(response));
                }
            }

            @Override
            public void onFailure(Call<AnswerModel> call, Throwable t) {
                callBack.loginOnFailure(t.getMessage());
            }
        });
    }

    public void sendCommand(CommandItem commandItem) {
        Call<AnswerModel> call = jsonPlaceHolderApi.sendCommand(commandItem);

        call.enqueue(new Callback<AnswerModel>() {
            @Override
            public void onResponse(Call<AnswerModel> call, Response<AnswerModel> response) {
                if (response.isSuccessful()) {
                    callBack.sendCommandSuccessful(response.body());
                }
            }

            @Override
            public void onFailure(Call<AnswerModel> call, Throwable t) {
                callBack.sendCommandOnFailure(t.getMessage());
            }
        });
    }
}