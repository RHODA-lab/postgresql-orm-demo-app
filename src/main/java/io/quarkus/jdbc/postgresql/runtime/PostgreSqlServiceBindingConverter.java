package io.quarkus.jdbc.postgresql.runtime;

import io.quarkus.kubernetes.service.binding.runtime.ServiceBinding;
import io.quarkus.kubernetes.service.binding.runtime.ServiceBindingConfigSource;
import io.quarkus.kubernetes.service.binding.runtime.ServiceBindingConverter;
import org.jboss.logging.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PostgreSqlServiceBindingConverter implements ServiceBindingConverter {
    public static final String QUARKUS_DATASOURCE_JDBC_URL = "quarkus.datasource.jdbc.url";
    public static final String BINDING_TYPE = "postgresql";
    private static final Logger log = Logger.getLogger(PostgreSqlServiceBindingConverter.class);

    @Override
    public Optional<ServiceBindingConfigSource> convert(List<ServiceBinding> serviceBindings) {

        Optional<ServiceBinding> matchingByType = ServiceBinding.singleMatchingByType(BINDING_TYPE, serviceBindings);
        if (!matchingByType.isPresent()) {
            return Optional.empty();
        }
        ServiceBinding binding = matchingByType.get();

        Optional<Map<String, String>> properties = buildBindingProperties(matchingByType, BINDING_TYPE, BINDING_TYPE);
        if (!properties.isPresent()) {
            return Optional.empty();
        }
        //process ssl params
        StringBuilder sslparam = new StringBuilder();
        String sslmode = binding.getProperties().getOrDefault("sslmode", "");
        String sslRootCert = binding.getProperties().getOrDefault("sslrootcert", "");
        if (!"".equals(sslmode)) {
            sslparam.append("sslmode=").append(sslmode);
        }
        if (!"".equals(sslRootCert)) {
            String and = "";
            if (!"".equals(sslmode)) and = "&";
            sslparam.append(and + "sslrootcert=")
                    .append(binding.getBindingDirectory() + "/" + sslRootCert);
        }

        String options = binding.getProperties().getOrDefault("options", "");
        try {
            options = options.length() > 0 ? "options=" + encoding(options.substring(1)).replace("+", "%20") : "";
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to encode options params" + options, e);
        }

        if (sslparam.length() > 0 && options.length() > 0) {
            options = sslparam + "&" + options;
        } else if (sslparam.length() > 0) {
            options = sslparam.toString();
        }

        if (!"".equals(options)) {
            properties.get().put(QUARKUS_DATASOURCE_JDBC_URL, properties.get().get(QUARKUS_DATASOURCE_JDBC_URL) + "?" + options);
        }

        return Optional.of(new ServiceBindingConfigSource(BINDING_TYPE + "-k8s-service-binding-source", properties.get()));
    }

    private String encoding(String str) throws UnsupportedEncodingException {
        return URLEncoder.encode(str, StandardCharsets.UTF_8.toString());
    }

    private Optional<Map<String, String>> buildBindingProperties(Optional<ServiceBinding> matchingByType, String bindingType, String urlType) {
        if (!matchingByType.isPresent()) {
            return Optional.empty();
        }

        Map<String, String> properties = new HashMap<>();
        ServiceBinding binding = matchingByType.get();

        String username = binding.getProperties().get("username");
        if (username != null) {
            properties.put("quarkus.datasource.username", username);
        } else {
            log.debug("Property 'username' was not found");
        }
        String password = binding.getProperties().get("password");
        if (password != null) {
            properties.put("quarkus.datasource.password", password);
        } else {
            log.debug("Property 'password' was not found");
        }
        String host = binding.getProperties().get("host");
        String port = binding.getProperties().get("port");
        String database = binding.getProperties().get("database");
        if ((host != null) && (database != null)) {
            String portPart = "";
            if (port != null) {
                portPart = ":" + port;
            }
            properties.put(QUARKUS_DATASOURCE_JDBC_URL, String.format("jdbc:%s://%s%s/%s", urlType, host, portPart, database));
        } else {
            log.debug("One or more of 'host' or 'database' properties were not found");
        }

        return Optional.of(properties);
    }
}
