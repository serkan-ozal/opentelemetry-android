package io.opentelemetry.instrumentation.library.okhttp.v3_0.internal;

public enum OkHttpEvent {

    DNS_RESOLVE("DNS Resolve"),
    SSL_HANDSHAKE("SSL Handshake");

    private final String name;

    OkHttpEvent(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
