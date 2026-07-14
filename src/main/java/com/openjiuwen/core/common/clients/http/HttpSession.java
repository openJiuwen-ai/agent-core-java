/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients.http;

import com.openjiuwen.core.common.clients.BaseRefResourceMgr;
import com.openjiuwen.core.common.clients.RefCountedResource;
import com.openjiuwen.core.common.clients.SessionConfig;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

/**
 * Wrapper for an HTTP client session with reference counting support.
 *
 * <p>Mirrors Python's {@code HttpSession} in
 * {@code openjiuwen/core/common/clients/http_client.py}.</p>
 */
public class HttpSession extends RefCountedResource {

    private final java.net.http.HttpClient session;
    private final SessionConfig config;
    private final boolean wrapStringResponses;
    private BaseRefResourceMgr.ResourceLease<HttpSession> lastLease;

    public HttpSession(java.net.http.HttpClient session, SessionConfig config) {
        this(session, config, false);
    }

    HttpSession(java.net.http.HttpClient session, SessionConfig config, boolean wrapStringResponses) {
        this.session = session;
        this.config = config;
        this.wrapStringResponses = wrapStringResponses;
    }

    public HttpSession(HttpClient session, SessionConfig config) {
        this(java.net.http.HttpClient.newHttpClient(), config);
    }

    public SessionConfig getConfig() {
        return config;
    }

    public SessionConfig config() {
        return config;
    }

    public BaseRefResourceMgr.ResourceLease<HttpSession> join() {
        return lastLease == null ? new BaseRefResourceMgr.ResourceLease<>(this, false) : lastLease;
    }

    void setLastLease(BaseRefResourceMgr.ResourceLease<HttpSession> lastLease) {
        this.lastLease = lastLease;
    }

    public java.net.http.HttpClient session() {
        if (isClosed()) {
            throw new IllegalStateException("Session is closed");
        }
        return wrapStringResponses ? new CompatibilityHttpClient(session) : session;
    }

    public void acquire() {
        incrementRef();
    }

    public void release() {
        if (decrementRef()) {
            close().join();
        }
    }

    @Override
    protected CompletableFuture<Void> doClose(Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(null);
    }

    private static final class CompatibilityHttpClient extends java.net.http.HttpClient {
        private final java.net.http.HttpClient delegate;

        private CompatibilityHttpClient(java.net.http.HttpClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return delegate.cookieHandler();
        }

        @Override
        public Optional<java.time.Duration> connectTimeout() {
            return delegate.connectTimeout();
        }

        @Override
        public Redirect followRedirects() {
            return delegate.followRedirects();
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return delegate.proxy();
        }

        @Override
        public SSLContext sslContext() {
            return delegate.sslContext();
        }

        @Override
        public SSLParameters sslParameters() {
            return delegate.sslParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return delegate.authenticator();
        }

        @Override
        public Version version() {
            return delegate.version();
        }

        @Override
        public Optional<Executor> executor() {
            return delegate.executor();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            return wrap(delegate.send(request, responseBodyHandler));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            return delegate.sendAsync(request, responseBodyHandler).thenApply(CompatibilityHttpClient::wrap);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return delegate.sendAsync(request, responseBodyHandler, pushPromiseHandler)
                    .thenApply(CompatibilityHttpClient::wrap);
        }

        @Override
        public WebSocket.Builder newWebSocketBuilder() {
            return delegate.newWebSocketBuilder();
        }

        @SuppressWarnings("unchecked")
        private static <T> HttpResponse<T> wrap(HttpResponse<T> response) {
            if (!(response.body() instanceof String rawBody)) {
                return response;
            }
            String compatibilityBody = HttpClient.compatibilityBody(response, rawBody);
            if (compatibilityBody.equals(rawBody)) {
                return response;
            }
            return new StringBodyResponse<>((HttpResponse<String>) response, (T) compatibilityBody);
        }
    }

    private static final class StringBodyResponse<T> implements HttpResponse<T> {
        private final HttpResponse<String> delegate;
        private final T body;

        private StringBodyResponse(HttpResponse<String> delegate, T body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return delegate.statusCode();
        }

        @Override
        public HttpRequest request() {
            return delegate.request();
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return delegate.headers();
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return delegate.sslSession();
        }

        @Override
        public java.net.URI uri() {
            return delegate.uri();
        }

        @Override
        public java.net.http.HttpClient.Version version() {
            return delegate.version();
        }
    }
}
