package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import consulo.build.ui.FilePosition;
import consulo.build.ui.event.BuildEvent;
import consulo.build.ui.event.BuildEventFactory;
import consulo.build.ui.event.MessageEvent;
import consulo.localize.LocalizeValue;
import consulo.project.ui.notification.NotificationGroup;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;
import org.jetbrains.idea.maven.localize.MavenRunnerLocalize;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

/**
 * @author UNV
 * @since 2026-06-22
 */
public class JavaBuildErrorNotificationTest {
    final BuildEventFactory myBuildEventFactory = mock(BuildEventFactory.class);
    final Object myParentId = new Object();
    final MavenParsingContext myParsingContext = mock(MavenParsingContext.class);
    final MavenLogEntryReader myMavenLogEntryReader = mock(MavenLogEntryReader.class);
    @SuppressWarnings("unchecked")
    final Consumer<? super BuildEvent> myMessageConsumer = mock(Consumer.class);

    final JavaBuildErrorNotification myNotification = new JavaBuildErrorNotification(myBuildEventFactory);

    @Test
    void supportsType() {
        assertThat(myNotification.supportsType(LogMessageType.DEBUG)).isFalse();
        assertThat(myNotification.supportsType(LogMessageType.INFO)).isFalse();
        assertThat(myNotification.supportsType(LogMessageType.WARNING)).isFalse();
        assertThat(myNotification.supportsType(LogMessageType.ERROR)).isTrue();
    }

    @Test
    void outOfMemory() {
        String line = "[ERROR] java.lang.OutOfMemoryError";
        assertThat(checkLogLine(line)).isTrue();

        Mockito.verify(myBuildEventFactory, times(1)).createMessageEvent(
            myParentId,
            MessageEvent.Kind.ERROR,
            JavaBuildErrorNotification.JAVA_COMPILER,
            MavenRunnerLocalize.buildEventMessageOutMemory(),
            LocalizeValue.of(line.substring("[ERROR] ".length()))
        );
    }

    @Test
    void noFileName() {
        assertThat(checkLogLine("[ERROR] Foobar")).isFalse();
    }

    @Test
    void withFileName() {
        Mockito.when(myParsingContext.toLocalFile(anyString()))
            .thenAnswer(invocation -> new File(invocation.getArgument(0, String.class)));

        LocalizeValue message = LocalizeValue.of("Garply!");
        File file = new File("/foo/Foobar.java");
        MessageEvent.Kind kind = MessageEvent.Kind.ERROR;
        NotificationGroup group = JavaBuildErrorNotification.JAVA_COMPILER;

        assertThat(checkLogLine("[ERROR] " + file + ": " + message)).isTrue();

        Mockito.verify(myBuildEventFactory, times(1))
            .createFileMessageEvent(myParentId, kind, group, message, message, new FilePosition(file));

        assertThat(checkLogLine("[ERROR] " + file + ":[123] " + message)).isTrue();

        Mockito.verify(myBuildEventFactory, times(1))
            .createFileMessageEvent(myParentId, kind, group, message, message, new FilePosition(file, 122, 0));

        assertThat(checkLogLine("[ERROR] " + file + ":[123,4] " + message)).isTrue();

        Mockito.verify(myBuildEventFactory, times(1))
            .createFileMessageEvent(myParentId, kind, group, message, message, new FilePosition(file, 122, 3));
    }

    private boolean checkLogLine(String line) {
        MavenLogEntryReader.MavenLogEntry logEntry = new MavenLogEntryReader.MavenLogEntry(line);
        return myNotification.checkLogLine(myParentId, myParsingContext, logEntry, myMavenLogEntryReader, myMessageConsumer);
    }
}
