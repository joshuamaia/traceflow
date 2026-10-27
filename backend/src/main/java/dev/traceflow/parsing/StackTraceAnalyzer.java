package dev.traceflow.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structural analysis of Java stack traces: exception chain, frames, and the probable origin of the failure.
 * <p>
 * The probable origin is the first application (non framework/JDK) frame of the deepest cause. It is only reported
 * when such a frame exists; otherwise the analysis states that the evidence is insufficient.
 */
public final class StackTraceAnalyzer {

    private static final Pattern FRAME = Pattern.compile(
            "^\\s*at\\s+(?:[\\w.$/@-]+/)?(?<cls>[\\w$.]+)\\.(?<method>[\\w$<>]+)\\((?<loc>[^)]*)\\)\\s*(?:~?\\[.*\\])?\\s*$");

    private static final List<String> FRAMEWORK_PREFIXES = List.of("java.", "javax.", "jakarta.", "jdk.", "sun.",
            "com.sun.", "org.springframework.", "org.hibernate.", "org.apache.", "com.zaxxer.", "io.netty.",
            "reactor.", "kotlin.", "scala.", "org.postgresql.", "com.mysql.", "oracle.", "com.fasterxml.",
            "tools.jackson.", "io.github.resilience4j.", "org.junit.", "net.bytebuddy.", "org.eclipse.jetty.",
            "io.undertow.", "ch.qos.logback.", "org.slf4j.", "feign.", "okhttp3.", "io.micrometer.", "org.aspectj.",
            "io.opentelemetry.", "com.google.", "org.jboss.", "io.grpc.", "org.mockito.", "org.h2.");

    public record Frame(String text, String className, String method, String location, boolean framework) {
    }

    public record ExceptionInfo(String className, String message, List<Frame> frames) {
    }

    public record ProbableOrigin(String className, String method, String location, String exceptionClass,
                                 String evidence) {
    }

    public record Analysis(List<ExceptionInfo> chain, ExceptionInfo rootCause, ProbableOrigin probableOrigin,
                           String note) {
    }

    private StackTraceAnalyzer() {
    }

    public static Analysis analyze(String stackTrace) {
        List<ExceptionInfo> chain = new ArrayList<>();
        if (stackTrace == null || stackTrace.isBlank()) {
            return new Analysis(chain, null, null, "No stack trace available for this event.");
        }
        String currentClass = null;
        String currentMessage = null;
        List<Frame> frames = new ArrayList<>();
        for (String line : stackTrace.lines().toList()) {
            Optional<StackTraces.Header> header = StackTraces.parseHeader(line);
            if (header.isPresent() && !line.trim().startsWith("Suppressed:")) {
                if (currentClass != null) {
                    chain.add(new ExceptionInfo(currentClass, currentMessage, List.copyOf(frames)));
                }
                currentClass = header.get().className();
                currentMessage = header.get().message();
                frames = new ArrayList<>();
                continue;
            }
            Matcher m = FRAME.matcher(line);
            if (m.matches()) {
                String cls = m.group("cls");
                frames.add(new Frame(line.trim(), cls, m.group("method"), m.group("loc"), isFramework(cls)));
            }
        }
        if (currentClass != null) {
            chain.add(new ExceptionInfo(currentClass, currentMessage, List.copyOf(frames)));
        }
        if (chain.isEmpty()) {
            return new Analysis(chain, null, null, "The stack trace could not be interpreted.");
        }
        ExceptionInfo root = chain.getLast();
        ProbableOrigin origin = null;
        String note;
        // walk from the deepest cause upwards until an application frame is found
        for (int i = chain.size() - 1; i >= 0 && origin == null; i--) {
            ExceptionInfo info = chain.get(i);
            for (Frame frame : info.frames()) {
                if (!frame.framework()) {
                    String evidence = (i == chain.size() - 1 ? "First application frame of the root cause "
                            : "First application frame of " + info.className() + " (root cause has no application frames) ")
                            + info.className();
                    origin = new ProbableOrigin(frame.className(), frame.method(), frame.location(), info.className(),
                            evidence.trim());
                    break;
                }
            }
        }
        note = origin == null
                ? "Insufficient evidence: no application frames were found (only JDK/framework frames)."
                : null;
        return new Analysis(chain, root, origin, note);
    }

    /** Returns "Class.method" of the first application frame, used by fingerprints. */
    public static Optional<String> firstApplicationFrame(String stackTrace) {
        if (stackTrace == null || stackTrace.isBlank()) {
            return Optional.empty();
        }
        for (String line : stackTrace.lines().toList()) {
            Matcher m = FRAME.matcher(line);
            if (m.matches() && !isFramework(m.group("cls"))) {
                return Optional.of(m.group("cls") + "." + m.group("method"));
            }
        }
        return Optional.empty();
    }

    public static boolean isFramework(String className) {
        for (String prefix : FRAMEWORK_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
