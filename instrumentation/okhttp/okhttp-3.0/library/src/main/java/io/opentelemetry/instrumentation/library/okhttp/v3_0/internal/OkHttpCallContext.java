package io.opentelemetry.instrumentation.library.okhttp.v3_0.internal;

import java.util.function.Consumer;

import io.opentelemetry.context.Context;

public class OkHttpCallContext {

    private final Context context;
    private final Consumer<Throwable> errorCallback;

    public OkHttpCallContext(Context context, Consumer<Throwable> errorCallback) {
        this.context = context;
        this.errorCallback = errorCallback;
    }

    public Context getContext() {
        return context;
    }

    public Consumer<Throwable> getErrorCallback() {
        return errorCallback;
    }
}
