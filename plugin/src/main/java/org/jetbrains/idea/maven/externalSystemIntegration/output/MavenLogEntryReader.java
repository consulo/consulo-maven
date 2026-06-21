// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import consulo.util.collection.SmartList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.function.Predicate;

public interface MavenLogEntryReader {
    void pushBack();

    @Nullable
    MavenLogEntry readLine();

    /**
     * Read lines while predicate is true
     */
    default List<MavenLogEntry> readWhile(Predicate<MavenLogEntry> logEntryPredicate) {
        List<MavenLogEntry> result = new SmartList<>();
        MavenLogEntry next;
        while ((next = readLine()) != null) {
            if (logEntryPredicate.test(next)) {
                result.add(next);
            }
            else {
                pushBack();
                break;
            }
        }
        return result;
    }

    /**
     * read first line which matches the predicate, other lines are ignored
     */
    default MavenLogEntry findFirst(Predicate<MavenLogEntry> logEntryPredicate) {
        MavenLogEntry result;
        while ((result = readLine()) != null) {
            if (logEntryPredicate.test(result)) {
                return result;
            }
        }
        return null;
    }

    public record MavenLogEntry(@Nullable LogMessageType type, @Nonnull String line) {
        public MavenLogEntry(@Nonnull String line) {
            String trimmedLine = clearProgressCarriageReturns(line);
            LogMessageType type = LogMessageType.determine(trimmedLine);
            this(type, clearLine(type, clearProgressCarriageReturns(trimmedLine)));
        }

        @Nonnull
        private static String clearProgressCarriageReturns(@Nonnull String line) {
            int i = line.lastIndexOf("\r");
            if (i == -1) return line;
            return line.substring(i + 1);
        }

        @Nonnull
        private static String clearLine(@Nullable LogMessageType type, @Nonnull String line) {
            return type == null ? line : type.clearLine(line);
        }

        @Override
        public String toString() {
            return type() == null ? line() : "[" + type() + "] " + line();
        }
    }
}
