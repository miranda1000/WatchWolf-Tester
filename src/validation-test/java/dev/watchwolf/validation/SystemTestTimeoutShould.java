package dev.watchwolf.validation;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * A system test without a timeout turns a hung server into a hung suite with no diagnostic — the
 * exact failure mode this framework exists to catch in other people's plugins.
 *
 * The suites inherited from the old flat layout do not declare one yet, so a missing timeout is
 * reported per file as <em>skipped, with the reason</em> rather than as a failure. Each file flips
 * to a pass on its own as it gains the annotation; once none are left, swap the {@code assumeTrue}
 * below for {@code assertTrue} to make this fatal, as it already is in WatchWolf-Core.
 */
public class SystemTestTimeoutShould {
    /** {@code @Timeout} on the line(s) immediately before the class declaration. */
    private static final Pattern TIMEOUT_ON_CLASS =
            Pattern.compile("@Timeout[^\\n]*\\n(?:\\s*@[^\\n]*\\n)*\\s*public\\s+class");

    @TestFactory
    public Stream<DynamicTest> declareATimeoutOnEverySystemTest() {
        Path root = TestNamingShould.SYSTEM_TESTS;

        return TestNamingShould.javaFilesIn(root).stream()
                .map(file -> dynamicTest(root.relativize(file).toString(), () -> {
                    String source;
                    try {
                        source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    } catch (IOException ex) {
                        throw new UncheckedIOException("Couldn't read " + file, ex);
                    }

                    assumeTrue(TIMEOUT_ON_CLASS.matcher(source).find(),
                            file.getFileName() + " has no @Timeout on its class; a hung server will hang the suite");
                }));
    }
}
