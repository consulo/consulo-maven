// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

/**
 * Exception thrown when a Maven server cannot be started.
 */
public class CannotStartServerException extends Exception {
    public CannotStartServerException(String message) {
        super(message);
    }

    public CannotStartServerException(String message, Throwable cause) {
        super(message, cause);
    }

    public CannotStartServerException(Throwable cause) {
        super(cause);
    }
}
