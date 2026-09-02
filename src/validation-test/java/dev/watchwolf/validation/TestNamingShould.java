package dev.watchwolf.validation;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * A test file that breaks the naming convention is silently never executed by Maven, which is how
 * this repository ended up with a suite nobody had run in years.
 *
 * One dynamic test per source file, so a violation names the offending file in the report instead
 * of failing the whole check.
 */
public class TestNamingShould {
    static final Path UNIT_TESTS = Paths.get("src/test/java");
    static final Path SYSTEM_TESTS = Paths.get("src/integration-test/java");
    static final Path VALIDATION_TESTS = Paths.get("src/validation-test/java");

    static List<Path> javaFilesIn(Path root) {
        if (!Files.isDirectory(root)) return java.util.Collections.emptyList();

        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            throw new UncheckedIOException("Couldn't walk " + root, ex);
        }
    }

    private static Stream<DynamicTest> forEachJavaFileIn(Path root, java.util.function.Consumer<Path> assertion) {
        return javaFilesIn(root).stream()
                .map(file -> dynamicTest(root.relativize(file).toString(), () -> assertion.accept(file)));
    }

    @TestFactory
    public Stream<DynamicTest> nameEveryUnitTestWithTheShouldSuffix() {
        return forEachJavaFileIn(UNIT_TESTS, file -> {
            String name = file.getFileName().toString();
            assertTrue(name.endsWith("Should.java"),
                    name + " is under " + UNIT_TESTS + " but does not end in 'Should', so Surefire will never run it");
        });
    }

    @TestFactory
    public Stream<DynamicTest> keepSystemTestsOutOfTheUnitSourceRoot() {
        return forEachJavaFileIn(UNIT_TESTS, file -> {
            String name = file.getFileName().toString();
            assertFalse(name.startsWith("IT"),
                    name + " starts with 'IT' but lives in " + UNIT_TESTS
                            + "; system tests belong in " + SYSTEM_TESTS + " and are run by Failsafe");
        });
    }

    @TestFactory
    public Stream<DynamicTest> nameEverySystemTestWithTheItPrefix() {
        return forEachJavaFileIn(SYSTEM_TESTS, file -> {
            String name = file.getFileName().toString();
            assertTrue(name.startsWith("IT"),
                    name + " is under " + SYSTEM_TESTS + " but does not start with 'IT', so Failsafe will never run it");
        });
    }

    @TestFactory
    public Stream<DynamicTest> nameEveryValidationCheckWithTheShouldSuffix() {
        return forEachJavaFileIn(VALIDATION_TESTS, file -> {
            String name = file.getFileName().toString();
            assertTrue(name.endsWith("Should.java"),
                    name + " is under " + VALIDATION_TESTS + " but does not end in 'Should', so it will never run");
        });
    }
}
