package com.etribunal.common.test;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Contenedor Testcontainer de Floci (emulador AWS local) para tests de integración.
 *
 * <p>Puertos expuestos por Floci: 4566 edge · 7001-7099 proxies RDS (puerto por instancia,
 * obtener vía DescribeDBInstances).</p>
 */
public class FlociContainer extends GenericContainer<FlociContainer> {

    public static final int EDGE_PORT = 4566;
    public static final DockerImageName DEFAULT_IMAGE = DockerImageName.parse("floci/floci:latest");

    public FlociContainer() {
        super(DEFAULT_IMAGE);
        withExposedPorts(EDGE_PORT);
        withReuse(true);
    }

    /** Endpoint del puerto edge, ej: http://localhost:32768 */
    public String getEndpoint() {
        return "http://" + getHost() + ":" + getMappedPort(EDGE_PORT);
    }
}
