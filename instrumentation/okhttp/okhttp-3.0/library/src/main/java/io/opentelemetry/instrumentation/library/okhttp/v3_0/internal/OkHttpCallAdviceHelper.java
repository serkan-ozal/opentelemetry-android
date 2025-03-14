/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.library.okhttp.v3_0.internal;

import java.util.function.Consumer;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import okhttp3.Call;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class OkHttpCallAdviceHelper {

    public static boolean propagateContext(Call call, Context context,
                                           Consumer<Throwable> errorCallback) {
        if (shouldPropagateContext(context)) {
            VirtualField<Call, OkHttpCallContext> virtualField =
                    VirtualField.find(Call.class, OkHttpCallContext.class);
            virtualField.set(call, new OkHttpCallContext(context, errorCallback));
            return true;
        }

        return false;
    }

    public static Context tryRecoverPropagatedContextFromCall(Call call) {
        VirtualField<Call, OkHttpCallContext> virtualField =
                VirtualField.find(Call.class, OkHttpCallContext.class);
        OkHttpCallContext callContext = virtualField.get(call);
        if (callContext != null) {
            return callContext.getContext();
        } else {
            return null;
        }
    }

    public static Consumer<Throwable> tryRecoverErrorCallbackFromCall(Call call) {
        VirtualField<Call, OkHttpCallContext> virtualField =
                VirtualField.find(Call.class, OkHttpCallContext.class);
        OkHttpCallContext callContext = virtualField.get(call);
        if (callContext != null) {
            return callContext.getErrorCallback();
        } else {
            return null;
        }
    }

    private static boolean shouldPropagateContext(Context context) {
        return context != Context.root();
    }
}
