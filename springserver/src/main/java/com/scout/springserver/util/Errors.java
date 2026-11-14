package com.scout.springserver.util;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class Errors {

    private Errors() {}

    /** True if any cause in the chain is an HTTP/socket timeout (axios ECONNABORTED / ETIMEDOUT). */
    public static boolean isTimeout(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }

    /** Unwraps the wrappers CompletableFuture adds, so callers see the real failure. */
    public static Throwable unwrap(Throwable error) {
        Throwable t = error;
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    public static String message(Throwable error) {
        if (error == null) return "Unknown error";
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }
}
