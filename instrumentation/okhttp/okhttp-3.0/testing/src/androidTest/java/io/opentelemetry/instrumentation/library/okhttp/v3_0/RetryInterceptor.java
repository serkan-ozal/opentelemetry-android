package io.opentelemetry.instrumentation.library.okhttp.v3_0;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class RetryInterceptor implements Interceptor {

    private static final int maxRetryCount = 3;
    private static final long retryDelayMillis = 500L;

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        int attempt = 0;
        IOException lastException = null;
        Response response = null;

        do {
            attempt++;

            System.out.println("Attempt " + attempt);

            try {
                Request modifiedRequest = chain.request().newBuilder()
                        .addHeader("X-Retry-Count", String.valueOf(attempt))
                        .build();

                response = chain.proceed(modifiedRequest);

                if (response.isSuccessful()) {
                    return response;
                }
            } catch (IOException e) {
                lastException = e;
            }

            // we need to close the response before retrying the request again
            if (response != null) {
                response.close();
            }

            if (attempt < maxRetryCount) {
                try {
                    TimeUnit.MILLISECONDS.sleep(attempt * retryDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } while (attempt < maxRetryCount);

        throw new IOException("Unknown error after " + maxRetryCount + " retries", lastException);
    }

}
