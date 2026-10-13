import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Build tool executed by JDK 17's source-file launcher after Spring Boot repackage.
 */
public final class TrimPlaywrightDriver {
    private static final Set<String> SUPPORTED_PLATFORMS =
            Set.of("linux", "linux-arm64", "win32_x64", "mac", "mac-arm64");

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: java TrimPlaywrightDriver.java <boot.jar> <platform[,platform]>"
                            + "; platforms: " + SUPPORTED_PLATFORMS);
        }
        Set<String> platforms = new LinkedHashSet<>();
        for (String value : args[1].split(",", -1)) {
            String platform = value.trim();
            if (!SUPPORTED_PLATFORMS.contains(platform)) {
                throw new IllegalArgumentException("Unsupported Playwright platform: " + value);
            }
            platforms.add(platform);
        }

        Path applicationJar = Path.of(args[0]).toAbsolutePath().normalize();
        long originalSize = Files.size(applicationJar);
        Path driverJar = Files.createTempFile(applicationJar.getParent(), ".playwright-driver-", ".tmp");
        Path trimmedDriverJar = null;
        Path trimmedApplicationJar = null;
        try {
            trimmedDriverJar = Files.createTempFile(applicationJar.getParent(), ".playwright-driver-trimmed-", ".tmp");
            trimmedApplicationJar = Files.createTempFile(applicationJar.getParent(), ".playwright-application-", ".tmp");
            String driverEntryName;
            try (ZipFile application = new ZipFile(applicationJar.toFile())) {
                var drivers = Collections.list(application.entries()).stream()
                        .filter(entry -> entry.getName().matches("BOOT-INF/lib/driver-bundle-[^/]+\\.jar"))
                        .toList();
                if (drivers.size() != 1) {
                    throw new IOException("Expected exactly one BOOT-INF/lib/driver-bundle-*.jar, found " + drivers.size());
                }
                driverEntryName = drivers.get(0).getName();
                try (InputStream source = application.getInputStream(drivers.get(0));
                     OutputStream destination = Files.newOutputStream(driverJar)) {
                    source.transferTo(destination);
                }
                trimDriver(driverJar, trimmedDriverJar, platforms);
                replaceDriver(application, trimmedApplicationJar, driverEntryName, trimmedDriverJar);
            }

            // Check the archive before replacing the original build artifact.
            try (ZipFile result = new ZipFile(trimmedApplicationJar.toFile())) {
                if (result.getEntry("META-INF/MANIFEST.MF") == null) {
                    throw new IOException("Missing application manifest");
                }
                for (ZipEntry entry : Collections.list(result.entries())) {
                    if (entry.getName().startsWith("BOOT-INF/lib/") && entry.getName().endsWith(".jar")
                            && entry.getMethod() != ZipEntry.STORED) {
                        throw new IOException("Spring Boot nested JAR must be STORED: " + entry.getName());
                    }
                }
            }
            try {
                Files.move(trimmedApplicationJar, applicationJar,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(trimmedApplicationJar, applicationJar, StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.printf(Locale.ROOT,
                    "Playwright platforms %s: driver %.2f -> %.2f MiB; application %.2f -> %.2f MiB%n",
                    platforms, mib(Files.size(driverJar)), mib(Files.size(trimmedDriverJar)),
                    mib(originalSize), mib(Files.size(applicationJar)));
        } finally {
            Files.deleteIfExists(driverJar);
            if (trimmedDriverJar != null) Files.deleteIfExists(trimmedDriverJar);
            if (trimmedApplicationJar != null) Files.deleteIfExists(trimmedApplicationJar);
        }
    }

    private static void trimDriver(Path original, Path destination, Set<String> platforms) throws IOException {
        try (ZipFile driver = new ZipFile(original.toFile());
             ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(destination))) {
            for (String platform : platforms) {
                String root = "driver/" + platform + "/";
                String node = platform.equals("win32_x64") ? "node.exe" : "node";
                String launcher = platform.equals("win32_x64") ? "playwright.cmd" : "playwright.sh";
                if (driver.getEntry(root) == null || driver.getEntry(root + node) == null
                        || driver.getEntry(root + launcher) == null) {
                    throw new IOException("Missing Playwright 1.38 driver resources for " + platform
                            + "; review this build tool when upgrading Playwright");
                }
            }
            Enumeration<? extends ZipEntry> entries = driver.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                // Keep all classes and metadata; filter only other platforms under driver/.
                if (name.startsWith("driver/") && !name.equals("driver/")
                        && platforms.stream().noneMatch(platform -> name.startsWith("driver/" + platform + "/"))) {
                    continue;
                }
                copyEntry(driver, entry, output);
            }
        }
    }

    private static void replaceDriver(ZipFile application, Path destination,
                                      String driverEntryName, Path driverJar) throws IOException {
        CRC32 crc = new CRC32();
        byte[] buffer = new byte[8192];
        try (InputStream driver = Files.newInputStream(driverJar)) {
            int count;
            while ((count = driver.read(buffer)) != -1) crc.update(buffer, 0, count);
        }
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(destination))) {
            Enumeration<? extends ZipEntry> entries = application.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().equals(driverEntryName)) {
                    copyEntry(application, entry, output);
                    continue;
                }
                ZipEntry replacement = new ZipEntry(driverEntryName);
                replacement.setTime(entry.getTime());
                // Spring Boot must seek within nested JARs, so outer compression is forbidden.
                replacement.setMethod(ZipEntry.STORED);
                replacement.setSize(Files.size(driverJar));
                replacement.setCompressedSize(Files.size(driverJar));
                replacement.setCrc(crc.getValue());
                output.putNextEntry(replacement);
                Files.copy(driverJar, output);
                output.closeEntry();
            }
        }
    }

    private static void copyEntry(ZipFile source, ZipEntry entry, ZipOutputStream output) throws IOException {
        ZipEntry copy = new ZipEntry(entry);
        if (copy.getMethod() == ZipEntry.DEFLATED) copy.setCompressedSize(-1);
        output.putNextEntry(copy);
        try (InputStream input = source.getInputStream(entry)) {
            input.transferTo(output);
        }
        output.closeEntry();
    }

    private static double mib(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }
}