package org.chainlink.infrastructure.deployment;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import com.vdurmont.semver4j.Semver;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class ClientDeploymentVerifier {
    public static final BiPredicate<Semver, Semver> DEFAULT_VERSION_MATCHER = Semver::isEqualTo;

    private final Semver serverVersion;
    @With
    // please note: SemVer hase various methods to compare versions!
    private final BiPredicate<Semver, Semver> versionMatcher;

    private ClientDeploymentVerifier(Semver serverVersion) {
        this.serverVersion = serverVersion;
        versionMatcher = DEFAULT_VERSION_MATCHER;
    }

    public static ClientDeploymentVerifier createDefault(Semver version) {
        return new ClientDeploymentVerifier(
            version
        );
    }

    public boolean clientIsCompatible(Semver clientVersion) {
        boolean match = compatibleVersion(clientVersion);

        if (match) {
            return true;
        }

        LOG.error(
            "Environment or Version not matching! Expected: {} - Actual: {}",
            serverVersion,
            clientVersion
        );

        return false;
    }

    public boolean clientIsCompatible(
        @Nullable String clientVersion
    ) {
        Semver version = tryParse(() -> new Semver(Objects.requireNonNull(clientVersion)));
        if (version == null) {
            LOG.warn("Not a valid client version: {}", clientVersion);
            return false;
        }

        boolean result = clientIsCompatible(version);

        return result;
    }

    private boolean compatibleVersion(Semver clientVersion) {
        return versionMatcher.test(serverVersion, clientVersion);
    }

    private <T> @Nullable T tryParse(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
