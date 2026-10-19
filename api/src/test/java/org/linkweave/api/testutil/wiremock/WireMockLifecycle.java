package org.linkweave.api.testutil.wiremock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Starts a WireMock server and points a set of config keys at it (NFR-032).
 *
 * <p>Subclasses name the keys. Attach a subclass with
 * {@code @WithTestResource(..., scope = TestResourceScope.RESTRICTED_TO_CLASS)}
 * on the one test class that needs it. A global {@code %test.} override would
 * redirect the same keys for every integration test and silently change what
 * the connection-refused assertions in other tests mean.
 */
public abstract class WireMockLifecycle implements QuarkusTestResourceLifecycleManager {

    private WireMockServer server;

    /** Config keys whose value becomes the stub's base URL. */
    protected abstract String[] baseUrlKeys();

    /** Extra fixed overrides, e.g. selecting a provider. */
    protected Map<String, String> extraConfig() {
        return Map.of();
    }

    @Override
    public Map<String, String> start() {
        // A fixed port, chosen up front, so a test can stop the stub to get a
        // genuinely refused connection and restart it on the same address.
        server = new WireMockServer(WireMockConfiguration.options().port(freePort()));
        server.start();
        Map<String, String> config = new HashMap<>(extraConfig());
        for (String key : baseUrlKeys()) {
            config.put(key, server.baseUrl());
        }
        return config;
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Override
    public void inject(TestInjector testInjector) {
        // Per instance, never static: Quarkus may start the next class's resource
        // before stopping this one, and a shared field would hand one class the
        // other's stub.
        testInjector.injectIntoFields(server, new TestInjector.AnnotatedAndMatchesType(
            InjectWireMock.class, WireMockServer.class));
    }
}
