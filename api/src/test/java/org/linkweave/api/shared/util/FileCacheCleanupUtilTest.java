package org.linkweave.api.shared.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class FileCacheCleanupUtilTest {

    @Test
    void shouldParseByteSuffixes() {
        assertThat(FileCacheCleanupUtil.parseSize("40MB")).isEqualTo(40L * 1024 * 1024);
        assertThat(FileCacheCleanupUtil.parseSize("1G")).isEqualTo(1024L * 1024 * 1024);
        assertThat(FileCacheCleanupUtil.parseSize("512")).isEqualTo(512L);
        assertThat(FileCacheCleanupUtil.parseSize("2KB")).isEqualTo(2048L);
    }

    @Test
    void shouldTolerateCaseAndWhitespace() {
        assertThat(FileCacheCleanupUtil.parseSize(" 5mb ")).isEqualTo(5L * 1024 * 1024);
        assertThat(FileCacheCleanupUtil.parseSize("1024b")).isEqualTo(1024L);
    }

    @Test
    void shouldRejectMalformedSize() {
        assertThatThrownBy(() -> FileCacheCleanupUtil.parseSize("not-a-number"))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void shouldReturnZeroForMissingDirectory(@TempDir Path tmp) {
        Path missing = tmp.resolve("does-not-exist");
        assertThat(FileCacheCleanupUtil.computeDirectorySize(missing, LoggerFactory.getLogger(getClass())))
            .isZero();
    }

    @Test
    void shouldSumFlatFileSizes(@TempDir Path tmp) throws IOException {
        // ARRANGE
        Files.write(tmp.resolve("a.bin"), new byte[100]);
        Files.write(tmp.resolve("b.meta"), new byte[42]);

        // ACT
        long total = FileCacheCleanupUtil.computeDirectorySize(tmp, LoggerFactory.getLogger(getClass()));

        // ASSERT
        assertThat(total).isEqualTo(142L);
    }

    @Test
    void shouldIgnoreSubdirectories(@TempDir Path tmp) throws IOException {
        // ARRANGE
        Files.write(tmp.resolve("flat.bin"), new byte[10]);
        Path nested = Files.createDirectory(tmp.resolve("nested"));
        Files.write(nested.resolve("deep.bin"), new byte[999]);

        // ACT
        long total = FileCacheCleanupUtil.computeDirectorySize(tmp, LoggerFactory.getLogger(getClass()));

        // ASSERT
        assertThat(total).isEqualTo(10L);
    }
}
