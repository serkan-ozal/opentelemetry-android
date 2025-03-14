package io.opentelemetry.instrumentation.library.okhttp.v3_0.internal;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import okhttp3.Interceptor;
import okhttp3.Response;

public class CallAwareTracingInterceptor implements Interceptor {

    private final Interceptor interceptor;

    public CallAwareTracingInterceptor(@NotNull Interceptor interceptor) {
        this.interceptor = interceptor;
    }

    @SuppressWarnings("MustBeClosedChecker")
    @NotNull
    @Override
    public Response intercept(@NotNull Interceptor.Chain chain) throws IOException {
        Context callContext =
                OkHttpCallAdviceHelper.tryRecoverPropagatedContextFromCall(chain.call());
        Scope callScope = null;
        try {
            if (callContext != null) {
                callScope = callContext.makeCurrent();
            }
            return interceptor.intercept(chain);
        } finally {
            if (callScope != null) {
                callScope.close();
            }
        }
    }
}
