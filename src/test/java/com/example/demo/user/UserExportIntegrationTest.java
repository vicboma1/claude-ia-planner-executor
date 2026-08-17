package com.example.demo.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for the user CSV export endpoint.
 *
 * <p>Boots the full application context on a random port and performs real HTTP
 * calls through {@link TestRestTemplate}. No mocks: the real controller, the
 * real service and the real Spring MVC message-conversion stack take part, so
 * this test also covers the "context loads" smoke check for the application.
 *
 * <p>Dependencies are injected through the constructor (project rule), which the
 * Spring TestContext framework supports via {@code SpringExtension} as a JUnit 5
 * {@code ParameterResolver}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Export CSV de usuarios - integración HTTP end-to-end")
class UserExportIntegrationTest {

    private static final String EXPORT_PATH = "/api/users/export";

    private final TestRestTemplate restTemplate;
    private final ApplicationContext applicationContext;
    private final UserService userService;

    @Autowired
    UserExportIntegrationTest(TestRestTemplate restTemplate,
                              ApplicationContext applicationContext,
                              UserService userService) {
        this.restTemplate = restTemplate;
        this.applicationContext = applicationContext;
        this.userService = userService;
    }

    private ResponseEntity<String> getExport() {
        return restTemplate.getForEntity(EXPORT_PATH, String.class);
    }

    @Test
    @DisplayName("el contexto de la aplicación arranca con el controller y el service reales")
    void contextLoadsWithRealControllerAndService() {
        // getBean lanza NoSuchBeanDefinitionException si el bean no existe: la propia llamada
        // es la aserción. Un isNotNull() adicional sería inalcanzable.
        applicationContext.getBean(UserController.class);
        applicationContext.getBean(UserService.class);
    }

    @Test
    @DisplayName("GET /api/users/export responde 200 OK")
    void exportEndpointRespondsOk() {
        ResponseEntity<String> response = getExport();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("el Content-Type real de la respuesta es text/plain con charset UTF-8, no application/json")
    void exportEndpointRespondsWithTextPlainContentType() {
        ResponseEntity<String> response = getExport();

        MediaType contentType = response.getHeaders().getContentType();

        assertThat(contentType).isNotNull();
        assertThat(contentType.getType()).isEqualTo("text");
        assertThat(contentType.getSubtype()).isEqualTo("plain");
        assertThat(contentType.getCharset()).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("el controller no transforma nada: el cuerpo HTTP coincide con el CSV del servicio")
    void controllerDelegatesToServiceWithoutTransformingPayload() {
        ResponseEntity<String> response = getExport();

        assertThat(response.getBody()).isEqualTo(userService.exportUsersCsv());
    }

    @Test
    @DisplayName("dos peticiones consecutivas devuelven el mismo estado y el mismo cuerpo")
    void repeatedRequestsReturnSameResponse() {
        ResponseEntity<String> first = getExport();
        ResponseEntity<String> second = getExport();

        assertThat(second.getStatusCode()).isEqualTo(first.getStatusCode());
        assertThat(second.getBody()).isEqualTo(first.getBody());
    }
}
