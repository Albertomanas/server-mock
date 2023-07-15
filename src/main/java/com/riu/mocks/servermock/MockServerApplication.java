package com.riu.mocks.servermock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

@SpringBootApplication
@RestController
@RequestMapping("/mock-generator")
public class MockServerApplication {
    private Map<String, Object> components;

    public static void main(String[] args) {
        SpringApplication.run(MockServerApplication.class, args);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> generateMocks() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Cargar el archivo YAML
            InputStream input = new FileInputStream("src/main/resources/static/example.yaml");

            // Analizar el archivo YAML
            Yaml yaml = new Yaml();
            Map<String, Object> yamlData = yaml.load(input);

            // Obtener los componentes
            components = (Map<String, Object>) yamlData.get("components");

            // Generar los mocks
            List<Map<String, Object>> mocks = generateMocksFromYaml(yamlData);
            response.put("mocks", mocks);
        } catch (Exception e) {
            response.put("error", "No se pudo generar los mocks: " + e.getMessage());
        }

        return response;
    }

    private List<Map<String, Object>> generateMocksFromYaml(Map<String, Object> yamlData) {
        List<Map<String, Object>> mocks = new ArrayList<>();

        // Recorrer los endpoints y generar los mocks
        Map<String, Object> paths = (Map<String, Object>) yamlData.get("paths");
        for (Map.Entry<String, Object> entry : paths.entrySet()) {
            String path = entry.getKey();
            Map<String, Object> endpointData = (Map<String, Object>) entry.getValue();

            // Buscar el objeto "get" dentro de "endpointData"
            Map<String, Object> getEndpoint = (Map<String, Object>) endpointData.get("get");
            if (getEndpoint != null) {
                // Obtener el objeto "responses" dentro de "get"
                Map<String, Object> responses = (Map<String, Object>) getEndpoint.get("responses");
                if (responses != null) {
                    for (Map.Entry<String, Object> responseEntry : responses.entrySet()) {
                        String statusCode = responseEntry.getKey();
                        Map<String, Object> response = (Map<String, Object>) responseEntry.getValue();

                        // Generar el mock para el código de respuesta actual
                        Object content = response.get("content");
                        if (content instanceof Map) {
                            Map<String, Object> contentMap = (Map<String, Object>) content;
                            for (Map.Entry<String, Object> mediaTypeEntry : contentMap.entrySet()) {
                                String mediaType = mediaTypeEntry.getKey();
                                Map<String, Object> mediaTypeData = (Map<String, Object>) mediaTypeEntry.getValue();
                                Map<String, Object> schema = (Map<String, Object>) mediaTypeData.get("schema");

                                // Generar el mock para el esquema actual
                                Object mockValue = generateMockValue(schema);
                                Map<String, Object> mock = new HashMap<>();
                                mock.put("endpoint", path);
                                mock.put("status_code", statusCode);
                                mock.put("media_type", mediaType);
                                mock.put("mock_value", mockValue);
                                mocks.add(mock);
                            }
                        }
                    }
                }
            }
        }

        return mocks;
    }

    private Object generateMockValue(Map<String, Object> schema) {

        String type = (String) schema.get("type");

        if (type != null) {
            switch (type) {
                case "string":
                    return generateRandomString();
                case "integer":
                    return generateRandomInteger();
                case "number":
                    return generateRandomNumber();
                case "boolean":
                    return generateRandomBoolean();
                case "array":
                    return generateRandomArray(schema);
                case "object":
                    return generateRandomObject(schema);
            }
        }

        return null;
    }

    private Map<String, Object> getReferencedSchema(String refSchema) {
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        return (Map<String, Object>) schemas.get(refSchema);
    }

    private String generateRandomString() {
        return UUID.randomUUID().toString();
    }

    private int generateRandomInteger() {
        return new Random().nextInt();
    }

    private double generateRandomNumber() {
        return new Random().nextDouble();
    }

    private boolean generateRandomBoolean() {
        return new Random().nextBoolean();
    }

    private List<Object> generateRandomArray(Map<String, Object> schema) {
        List<Object> array = new ArrayList<>();
        Map<String, Object> items = (Map<String, Object>) schema.get("items");
        int minItems = (int) schema.getOrDefault("minItems", 1);
        int maxItems = (int) schema.getOrDefault("maxItems", 5);
        int itemCount = new Random().nextInt(maxItems - minItems + 1) + minItems;

        for (int i = 0; i < itemCount; i++) {
            array.add(generateMockValue(items));
        }

        return array;
    }

    private Map<String, Object> generateRandomObject(Map<String, Object> schema) {
        Map<String, Object> object = new LinkedHashMap<>();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        for (Map.Entry<String, Object> propertyEntry : properties.entrySet()) {
            String propertyName = propertyEntry.getKey();
            Map<String, Object> propertySchema = (Map<String, Object>) propertyEntry.getValue();
            Object propertyValue = generateMockValue(propertySchema);
            object.put(propertyName, propertyValue);
        }

        return object;
    }
}
