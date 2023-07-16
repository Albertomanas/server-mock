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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@SpringBootApplication
@RestController
@RequestMapping("/mock-generator")
public class MockServerApplication {
    private Map<String, Object> components;
    private Set<String> processedRefs;
    private Map<String, Object> processedRefsMockValues; // Nuevo mapa para almacenar los valores generados por referencia

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
            processedRefs = new HashSet<>();
            processedRefsMockValues = new HashMap<>(); // Inicializar el mapa de valores generados por referencia

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

            // Buscar el objeto de método dentro de endpointData
            for (Map.Entry<String, Object> methodEntry : endpointData.entrySet()) {
                String method = methodEntry.getKey();
                if (method.equals("get") || method.equals("post") || method.equals("put") || method.equals("delete")) {
                    Map<String, Object> methodData = (Map<String, Object>) methodEntry.getValue();
                    String statusCode = ""; // Obtener el código de estado correspondiente

                    // Obtener el objeto "responses" dentro del objeto de método
                    Map<String, Object> responses = (Map<String, Object>) methodData.get("responses");
                    if (responses != null) {
                        for (Map.Entry<String, Object> responseEntry : responses.entrySet()) {
                            statusCode = responseEntry.getKey();
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
                                    mock.put("request_method", method); // Agregar el tipo de solicitud al mock
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
        }

        return mocks;
    }

    private Object generateMockValue(Map<String, Object> schema) {
        String ref = (String) schema.get("$ref");

        if (ref != null) {
            String[] refParts = ref.split("/");
            String refSchema = refParts[refParts.length - 1];
            if (!processedRefs.contains(refSchema)) {
                processedRefs.add(refSchema);
                Map<String, Object> referencedSchema = getReferencedSchema(refSchema);
                if (referencedSchema != null) {
                    Object referencedMockValue = generateMockValue(referencedSchema);
                    processedRefsMockValues.put(ref, referencedMockValue); // Almacenar el valor mockeado generado por la referencia
                    return referencedMockValue;
                }
            } else {
                // Referencia ya procesada, retornar el valor anteriormente generado
                return processedRefsMockValues.get(ref);
            }
        }

        String type = (String) schema.get("type");

        if (type != null) {
            switch (type) {
                case "string":
                    if (schema.containsKey("enum")) {
                        List<String> enumValues = (List<String>) schema.get("enum");
                        return enumValues.get(new Random().nextInt(enumValues.size()));
                    }
                    if (schema.containsKey("format") && schema.get("format").equals("date-time")) {
                        return generateRandomDateTime();
                    }
                    return generateRandomString();
                case "integer":
                    if (schema.containsKey("format") && schema.get("format").equals("int64")) {
                        return generateRandomLong();
                    }
                    return generateRandomInteger();
                case "number":
                    return generateRandomNumber();
                case "boolean":
                    return generateRandomBoolean();
                case "array":
                    Object items = schema.get("items");
                    if (items instanceof Map) {
                        Map<String, Object> itemsSchema = (Map<String, Object>) items;
                        int minItems = (int) schema.getOrDefault("minItems", 1);
                        int maxItems = (int) schema.getOrDefault("maxItems", 5);
                        int itemCount = new Random().nextInt(maxItems - minItems + 1) + minItems;
                        List<Object> array = new ArrayList<>();
                        for (int i = 0; i < itemCount; i++) {
                            array.add(generateMockValue(itemsSchema));
                        }
                        return array;
                    }
                    break;
                case "object":
                    return generateRandomObject(schema);
            }
        }

        return null;
    }

    private Object generateRandomDateTime() {
        // Obtener la fecha y hora actual
        LocalDateTime currentDateTime = LocalDateTime.now();

        // Generar una fecha y hora aleatoria entre el año 2000 y el año actual
        LocalDateTime randomDateTime = LocalDateTime.of(
                getRandomNumber(2000, currentDateTime.getYear()),
                getRandomNumber(1, 12),
                getRandomNumber(1, 28),
                getRandomNumber(0, 23),
                getRandomNumber(0, 59),
                getRandomNumber(0, 59)
        );

        // Convertir la fecha y hora a un valor compatible con el formato deseado
        return randomDateTime.toInstant(ZoneOffset.UTC).toString();
    }

    private int getRandomNumber(int min, int max) {
        // Generar un número entero aleatorio en el rango especificado
        return min + (int) (Math.random() * (max - min + 1));
    }

    private long generateRandomLong() {
        return new Random().nextLong();
    }

    private Map<String, Object> generateRandomObject(Map<String, Object> schema) {
        Map<String, Object> object = new LinkedHashMap<>();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        if (properties != null) {
            for (Map.Entry<String, Object> propertyEntry : properties.entrySet()) {
                String propertyName = propertyEntry.getKey();
                Map<String, Object> propertySchema = (Map<String, Object>) propertyEntry.getValue();
                Object propertyValue = generateMockValue(propertySchema);
                object.put(propertyName, propertyValue);
            }
        }

        return object;
    }

    private Map<String, Object> getReferencedSchema(String refSchema) {
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        if (schemas != null) {
            Object referencedSchema = schemas.get(refSchema);
            if (referencedSchema instanceof Map) {
                return (Map<String, Object>) referencedSchema;
            }
        }
        return null;
    }

    private String generateRandomString() {
        // Generar una cadena de texto aleatoria basada en un lorem ipsum de 10 palabras
        String loremIpsum = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed in pharetra nisi, auctor tempus lorem.";
        String[] words = loremIpsum.split("\\s+");
        StringBuilder stringBuilder = new StringBuilder();

        // Generar una cadena con 10 palabras aleatorias del lorem ipsum
        int maxWords = 4;
        for (int i = 0; i < maxWords; i++) {
            int randomIndex = new Random().nextInt(words.length);
            String randomWord = words[randomIndex];
            stringBuilder.append(randomWord).append(" ");
        }

        return stringBuilder.toString().trim();
    }

    private int generateRandomInteger() {
        // Generar un número entero aleatorio positivo con máximo 4 dígitos
        int maxDigits = 4;
        int maxNumber = (int) Math.pow(10, maxDigits) - 1;
        return 1 + new Random().nextInt(maxNumber);
    }

    private double generateRandomNumber() {
        // Generar un número entero aleatorio positivo con máximo 4 dígitos
        int maxDigits = 4;
        int maxNumber = (int) Math.pow(10, maxDigits) - 1;
        return 1 + new Random().nextDouble(maxNumber);
    }

    private boolean generateRandomBoolean() {
        return new Random().nextBoolean();
    }
}
