package io.opentelemetry.instrumentation.library.okhttp.v3_0.internal;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.Consumer;

import okhttp3.Interceptor;
import okhttp3.Response;

public class ErrorAwareInterceptor implements Interceptor {

    @SuppressWarnings("MustBeClosedChecker")
    @NotNull
    @Override
    public Response intercept(@NotNull Interceptor.Chain chain) throws IOException {
        try {
            return chain.proceed(chain.request());
        } catch (IOException e) {
            Consumer<Throwable> errorCallback =
                    OkHttpCallAdviceHelper.tryRecoverErrorCallbackFromCall(chain.call());
            if (errorCallback != null) {
                errorCallback.accept(e);
            }
            throw e;
        }
    }
}
