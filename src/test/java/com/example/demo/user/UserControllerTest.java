package com.example.demo.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link UserController}.
 *
 * <p>Scope: only the MVC infrastructure is loaded ({@code @WebMvcTest}); {@link UserService} is
 * replaced by a Mockito mock, so nothing here exercises real business logic. The goal is to pin
 * the HTTP contract of {@code GET /api/users/export} and prove the controller merely delegates.
 *
 * <p>Mock annotation note: this project targets Spring Boot 3.3.0 (Spring Framework 6.1.x).
 * {@code org.springframework.test.context.bean.override.mockito.MockitoBean} only ships with
 * Spring Framework 6.2 / Spring Boot 3.4+, so it does <em>not</em> exist on this classpath.
 * {@link MockBean} is the correct (and not-yet-deprecated) annotation here. When this project
 * upgrades to Boot 3.4+, swap it for {@code @MockitoBean}.
 *
 * <p>Content-type note: the handler returns a bare {@code String} with no {@code produces}
 * attribute, so Spring serialises it with {@code StringHttpMessageConverter}, which negotiates
 * {@code text/plain} (not {@code application/json}) and appends the default charset. The
 * concrete value asserted below was verified against a real run, not assumed.
 */
@WebMvcTest(UserController.class)
@DisplayName("UserController web layer")
class UserControllerTest {

    private static final String EXPORT_PATH = "/api/users/export";

    /**
     * Deliberately different from the production value returned by {@code UserService}
     * ("id,name,email\n1,john,john@test.com"). If the controller ever hardcodes or post-processes
     * the payload, the body assertions below fail instead of accidentally passing.
     */
    private static final String STUBBED_CSV = "id,name,email\n42,mock-user,mock@stub.invalid";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("GET /api/users/export returns 200 with the exact body produced by the service")
    void exportReturnsServiceBodyVerbatim() throws Exception {
        given(userService.exportUsersCsv()).willReturn(STUBBED_CSV);

        mockMvc.perform(get(EXPORT_PATH))
                .andExpect(status().isOk())
                .andExpect(content().string(STUBBED_CSV));
    }

    @Test
    @DisplayName("GET /api/users/export delegates to UserService exactly once")
    void exportDelegatesToServiceExactlyOnce() throws Exception {
        given(userService.exportUsersCsv()).willReturn(STUBBED_CSV);

        mockMvc.perform(get(EXPORT_PATH)).andExpect(status().isOk());

        verify(userService, times(1)).exportUsersCsv();
        verifyNoMoreInteractions(userService);
    }

    @Test
    @DisplayName("Response Content-Type is text/plain;charset=UTF-8, not application/json")
    void exportRespondsAsPlainTextUtf8() throws Exception {
        given(userService.exportUsersCsv()).willReturn(STUBBED_CSV);

        mockMvc.perform(get(EXPORT_PATH))
                .andExpect(status().isOk())
                // Documented contract: a String return value is written by
                // StringHttpMessageConverter as text/plain with the default UTF-8 charset.
                .andExpect(content().contentType("text/plain;charset=UTF-8"));
    }

    @Test
    @DisplayName("Endpoint sends no Content-Disposition header, so it is an inline body, not a download")
    void exportIsInlineBodyNotFileDownload() throws Exception {
        given(userService.exportUsersCsv()).willReturn(STUBBED_CSV);

        // Pinning current behaviour: despite the "export" name, this is NOT a file download.
        // If a Content-Disposition/text-csv contract is ever added, this test must be updated
        // deliberately rather than silently.
        mockMvc.perform(get(EXPORT_PATH))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    @DisplayName("An empty payload from the service is passed through as an empty 200 body")
    void exportPassesThroughEmptyPayload() throws Exception {
        given(userService.exportUsersCsv()).willReturn("");

        mockMvc.perform(get(EXPORT_PATH))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(userService).exportUsersCsv();
    }

    @Test
    @DisplayName("FINDING: the endpoint echoes back any Accept type, because no 'produces' is declared")
    void exportEchoesAnyRequestedContentType() throws Exception {
        given(userService.exportUsersCsv()).willReturn(STUBBED_CSV);

        // Verified behaviour, and a genuine defect worth recording:
        // StringHttpMessageConverter advertises text/plain AND */*, so it can satisfy every
        // Accept header. Content negotiation therefore never rejects anything and no 406 is ever
        // produced. The response mislabels a CSV payload as whatever the client asked for.
        // Asserting only the defect itself (no 406, body unchanged) and not the concrete
        // Content-Type: that value depends on StringHttpMessageConverter internals, which have
        // changed across Spring versions (the default charset became UTF-8 in Framework 6.0).
        mockMvc.perform(get(EXPORT_PATH).header(HttpHeaders.ACCEPT, "image/png"))
                .andExpect(status().isOk())
                .andExpect(content().string(STUBBED_CSV));

        // Worse: a JSON client gets a 200 with a body that is not JSON, because
        // StringHttpMessageConverter is consulted before the Jackson converter.
        String jsonBody = mockMvc.perform(get(EXPORT_PATH).header(HttpHeaders.ACCEPT, "application/json"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(jsonBody)
                .as("body is raw CSV, not a JSON document, despite the application/json request")
                .isEqualTo(STUBBED_CSV);

        // Fixing this belongs in src/main (add produces = "text/csv" to the mapping); this test
        // documents today's contract so the fix is a deliberate, visible change.
    }

    @Test
    @DisplayName("Non-GET methods on /api/users/export return 405 with an Allow header")
    void nonGetMethodsReturnMethodNotAllowed() throws Exception {
        mockMvc.perform(post(EXPORT_PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString(HttpMethod.GET.name())));

        mockMvc.perform(put(EXPORT_PATH)).andExpect(status().isMethodNotAllowed());

        verify(userService, never()).exportUsersCsv();
    }

    @Test
    @DisplayName("A service failure is not swallowed by the controller")
    void serviceFailurePropagates() {
        given(userService.exportUsersCsv())
                .willThrow(new IllegalStateException("export backend unavailable"));

        // There is no @ControllerAdvice / @ExceptionHandler in this application, so an unchecked
        // service failure escapes the handler untouched. MockMvc surfaces it (wrapped by the
        // servlet container contract) instead of returning a body; in a real container this is a
        // 500 rendered by Spring Boot's default error handling.
        assertThatThrownBy(() -> mockMvc.perform(get(EXPORT_PATH)))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("export backend unavailable");

        verify(userService).exportUsersCsv();
    }

    @Test
    @DisplayName("The controller passes no arguments and holds no state between requests")
    void controllerIsStatelessAndArgumentFree() throws Exception {
        given(userService.exportUsersCsv()).willReturn(STUBBED_CSV);

        mockMvc.perform(get(EXPORT_PATH)).andExpect(content().string(STUBBED_CSV));
        mockMvc.perform(get(EXPORT_PATH)).andExpect(content().string(STUBBED_CSV));

        // Guards the "no business logic in controllers" rule: the only collaboration is a single
        // no-arg call per request, with no caching or memoisation in the web layer.
        verify(userService, times(2)).exportUsersCsv();
        verifyNoMoreInteractions(userService);
    }
}
