package org.jetbrains.idea.maven.externalSystemIntegration.output;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author UNV
 * @since 2026-06-22
 */
public class LogMessageTypeTest {
    @Test
    void startsWith() {
        assertThat(LogMessageType.determine("[DEBUG] Foobar")).isSameAs(LogMessageType.DEBUG);
        assertThat(LogMessageType.determine("[INFO] Foobar")).isSameAs(LogMessageType.INFO);
        assertThat(LogMessageType.determine("[WARNING] Foobar")).isSameAs(LogMessageType.WARNING);
        assertThat(LogMessageType.determine("[ERROR] Foobar")).isSameAs(LogMessageType.ERROR);
        assertThat(LogMessageType.determine("[FATAL] Foobar")).isNull();
    }

    @Test
    void contains() {
        assertThat(LogMessageType.determine("2026-01-01 00:00:01 [DEBUG] Foobar")).isSameAs(LogMessageType.DEBUG);
        assertThat(LogMessageType.determine("2026-01-01 00:00:01 [INFO] Foobar")).isSameAs(LogMessageType.INFO);
        assertThat(LogMessageType.determine("2026-01-01 00:00:01 [WARNING] Foobar")).isSameAs(LogMessageType.WARNING);
        assertThat(LogMessageType.determine("2026-01-01 00:00:01 [ERROR] Foobar")).isSameAs(LogMessageType.ERROR);
        assertThat(LogMessageType.determine("2026-01-01 00:00:01 [FATAL] Foobar")).isNull();
    }

    @Test
    void ambiguity() {
        assertThat(LogMessageType.determine("[DEBUG] Foobar [INFO]")).isSameAs(LogMessageType.DEBUG);
        assertThat(LogMessageType.determine("[INFO] Foobar [DEBUG]")).isSameAs(LogMessageType.INFO);
        assertThat(LogMessageType.determine("[WARNING] Foobar [DEBUG]")).isSameAs(LogMessageType.WARNING);
        assertThat(LogMessageType.determine("[ERROR] Foobar [DEBUG]")).isSameAs(LogMessageType.ERROR);
        assertThat(LogMessageType.determine("[FATAL] Foobar [DEBUG]")).isNull();
    }
}
