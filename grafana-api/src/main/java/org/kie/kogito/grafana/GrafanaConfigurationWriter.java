/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.kogito.grafana;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import org.kie.dmn.model.api.Decision;
import org.kie.kogito.KogitoGAV;
import org.kie.kogito.grafana.dmn.SupportedDecisionTypes;
import org.kie.kogito.grafana.model.functions.GrafanaFunction;
import org.kie.kogito.grafana.model.functions.Label;
import org.kie.kogito.grafana.model.panel.PanelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrafanaConfigurationWriter {

    private static final Logger logger = LoggerFactory.getLogger(GrafanaConfigurationWriter.class);

    private static final String AUDIT_LINK_NAME = "Audit UI";
    private static final String AUDIT_LINK_URL_PLACEHOLDER = "${{urlPlaceholder}}";

    private GrafanaConfigurationWriter() {
        // Intentionally left blank.
    }

    /**
     * Generates an operational grafana dashboard based on a given template.
     *
     * @param templatePath: The path to the dashboard template. It must be a valid grafana dashboard in JSON format.
     * @param handlerName: The name of the endpoint.
     * @return: The template customized for the endpoint.
     */
    public static String generateOperationalDashboard(String templatePath, String dashboardName, String handlerName, KogitoGAV gav, boolean generateAuditLink) {
        String template = readStandardDashboard(templatePath);
        template = customizeTemplate(template, handlerName, gav.getArtifactId(), gav.getVersion());

        JGrafana jgrafana = initialize(template, String.format("%s - Operational Dashboard", dashboardName), generateAuditLink);

        return serialize(jgrafana);
    }

    /**
     * Generates domain specific dashboard from a given dashboard template.
     *
     * @param templatePath: The path to the dashboard template. It must be a valid grafana dashboard in JSON format.
     * @param endpoint: The name of the endpoint.
     * @param decisions: The decisions in the DMN model.
     * @return: The customized template containing also specific panels for the DMN decisions that have been specified in the arguments.
     */
    public static String generateDomainSpecificDMNDashboard(String templatePath, String dashboardName, String endpoint, KogitoGAV gav, List<Decision> decisions, boolean generateAuditLink) {
        String template = readStandardDashboard(templatePath);
        template = customizeTemplate(template, endpoint, gav.getArtifactId(), gav.getVersion());

        JGrafana jgrafana = initialize(template, String.format("%s - Domain Dashboard", dashboardName), generateAuditLink);

        for (Decision decision : decisions) {
            QName type = decision.getVariable().getTypeRef();
            if (type == null) {
                logger.warn(String.format("DMN typeref for the decision \"%s\" with node id \"%s\" is null.", decision.getName(), decision.getId()));
            } else {
                if (SupportedDecisionTypes.isSupported(type.getLocalPart())) {
                    String metricBody = "dmn_result";
                    List<Label> labels = new ArrayList<>();
                    labels.add(new Label("endpoint", "\"" + endpoint + "\""));
                    labels.add(new Label("decision", "\"" + decision.getName() + "\""));
                    labels.add(new Label("artifactId", "\"" + gav.getArtifactId() + "\""));
                    labels.add(new Label("version", "\"" + gav.getVersion() + "\""));

                    GrafanaFunction grafanaFunction = SupportedDecisionTypes.getGrafanaFunction(type.getLocalPart())
                            .orElseThrow(() -> new RuntimeException("Mismatch between supported Grafana DMN Types and defined functions"));

                    jgrafana.addPanel(PanelType.GRAPH,
                            "Decision " + decision.getName(),
                            grafanaFunction.render(metricBody, labels),
                            SupportedDecisionTypes.getYAxis(type.getLocalPart()));
                }
            }
        }

        return serialize(jgrafana);
    }

    /**
     * Generates domain specific DRL dashboard from a given dashboard template.
     *
     * @param templatePath: The path to the dashboard template. It must be a valid grafana dashboard in JSON format.
     * @param endpoint: The name of the endpoint.
     * @return: The customized template containing also specific panels for the DMN decisions that have been specified in the arguments.
     */
    public static String generateDomainSpecificDrlDashboard(String templatePath, String dashboardName, String endpoint, KogitoGAV gav, boolean generateAuditLink) {
        String template = readStandardDashboard(templatePath);
        template = customizeTemplate(template, endpoint, gav.getArtifactId(), gav.getVersion());

        JGrafana jgrafana = initialize(template, String.format("%s - Domain Dashboard", dashboardName), generateAuditLink);

        return serialize(jgrafana);
    }

    private static JGrafana initialize(String template, String name, boolean generateAuditLink) {
        JGrafana jgrafana;
        try {
            jgrafana = JGrafana.parse(template).setTitle(name);
        } catch (IOException e) {
            logger.error(String.format("Could not parse the grafana template for the dashboard %s", name), e);
            throw new IllegalArgumentException("Could not parse the dashboard template.", e);
        }

        if (generateAuditLink) {
            jgrafana.addLink(AUDIT_LINK_NAME, AUDIT_LINK_URL_PLACEHOLDER);
        }
        return jgrafana;
    }

    private static String serialize(JGrafana jgrafana) {
        try {
            return jgrafana.serialize();
        } catch (IOException e) {
            logger.error("Could not serialize the grafana dashboard");
            throw new UncheckedIOException("Could not serialize the grafana dashboard.", e);
        }
    }

    public static String buildDashboardName(Optional<KogitoGAV> gav, String handlerName) {
        if (gav.isPresent()) {
            return String.format("%s_%s - %s", gav.get().getArtifactId(), gav.get().getVersion(), handlerName);
        }
        return handlerName;
    }

    private static String readStandardDashboard(String templatePath) {
        InputStream is = GrafanaConfigurationWriter.class.getResourceAsStream(templatePath);
        return new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"));
    }

    private static String customizeTemplate(String template, String handlerName, String artifactId, String version) {
        template = template.replaceAll("\\$handlerName\\$", handlerName);
        template = template.replaceAll("\\$id\\$", String.valueOf(new Random().nextInt()));
        template = template.replaceAll("\\$uid\\$", UUID.randomUUID().toString());
        template = template.replaceAll("\\$gavArtifactId\\$", artifactId);
        template = template.replaceAll("\\$gavVersion\\$", version);
        return template;
    }
}
