package io.github.connellite.microorm;

import org.junit.jupiter.api.Assumptions;
import org.opentest4j.TestAbortedException;
import org.testcontainers.DockerClientFactory;

final class TestcontainersSupport {

    private TestcontainersSupport() {
    }

    static void assumeDockerAvailable() {
        try {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        } catch (RuntimeException | LinkageError e) {
            if (e instanceof TestAbortedException) {
                throw e;
            }
            throw new TestAbortedException("Docker is not available", e);
        }
    }
}
