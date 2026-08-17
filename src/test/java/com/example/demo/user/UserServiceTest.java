package com.example.demo.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the CSV export contract exposed by {@link UserService}.
 *
 * <p>The CSV produced by the service is a public data contract: any consumer
 * (spreadsheet, ETL job, downstream service) depends on the exact header, the
 * column order, the column count per row and the field values. These tests
 * pin that contract down field by field instead of doing loose substring
 * checks, so a silent format change breaks the build instead of the consumer.
 */
@DisplayName("UserService - contrato del export CSV")
class UserServiceTest {

    /** Golden master: único punto de la suite donde se fija el CSV literal completo. */
    private static final String EXPECTED_CSV = "id,name,email\n1,john,john@test.com";
    private static final List<String> EXPECTED_COLUMNS = List.of("id", "name", "email");
    private static final String COLUMN_SEPARATOR = ",";

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService();
    }

    /**
     * Splits keeping trailing empty strings (limit -1) so that a stray trailing
     * line separator is visible to the assertions instead of being swallowed.
     */
    private static List<String> linesOf(String csv) {
        return Arrays.asList(csv.split("\n", -1));
    }

    private static List<String> fieldsOf(String line) {
        return Arrays.asList(line.split(COLUMN_SEPARATOR, -1));
    }

    private static String fieldByColumnName(String csv, String columnName) {
        List<String> lines = linesOf(csv);
        int columnIndex = fieldsOf(lines.get(0)).indexOf(columnName);
        assertThat(columnIndex)
                .as("la columna '%s' debe existir en la cabecera", columnName)
                .isNotNegative();
        assertThat(lines)
                .as("el CSV debe tener al menos una fila de datos")
                .hasSizeGreaterThan(1);
        return fieldsOf(lines.get(1)).get(columnIndex);
    }

    @Nested
    @DisplayName("Resultado global")
    class Result {

        @Test
        @DisplayName("devuelve exactamente el CSV esperado (cabecera + 1 fila)")
        void returnsExactExpectedCsv() {
            String csv = userService.exportUsersCsv();

            assertThat(csv).isEqualTo(EXPECTED_CSV);
        }
    }

    @Nested
    @DisplayName("Cabecera")
    class Header {

        @Test
        @DisplayName("declara las tres columnas esperadas y en ese orden")
        void headerDeclaresExpectedColumnsInOrder() {
            String csv = userService.exportUsersCsv();

            List<String> headerFields = fieldsOf(linesOf(csv).get(0));

            assertThat(headerFields).containsExactlyElementsOf(EXPECTED_COLUMNS);
        }

        @Test
        @DisplayName("ninguna columna de la cabecera está vacía ni tiene espacios sobrantes")
        void headerColumnsAreTrimmedAndNotEmpty() {
            String csv = userService.exportUsersCsv();

            assertThat(fieldsOf(linesOf(csv).get(0)))
                    .allSatisfy(column -> assertThat(column).isNotEmpty().isEqualTo(column.trim()));
        }
    }

    @Nested
    @DisplayName("Estructura de líneas")
    class Structure {

        @Test
        @DisplayName("contiene una cabecera y exactamente una fila de datos")
        void containsHeaderAndSingleDataRow() {
            String csv = userService.exportUsersCsv();

            assertThat(linesOf(csv)).hasSize(2);
        }

        @Test
        @DisplayName("cada fila de datos tiene el mismo número de columnas que la cabecera")
        void everyDataRowHasSameColumnCountAsHeader() {
            String csv = userService.exportUsersCsv();
            List<String> lines = linesOf(csv);
            int headerColumnCount = fieldsOf(lines.get(0)).size();

            List<String> dataLines = lines.subList(1, lines.size());

            assertThat(dataLines)
                    .isNotEmpty()
                    .allSatisfy(line -> assertThat(fieldsOf(line))
                            .as("columnas de la línea '%s'", line)
                            .hasSize(headerColumnCount));
        }

        @Test
        @DisplayName("no contiene líneas vacías (tampoco un salto de línea final)")
        void containsNoEmptyLines() {
            String csv = userService.exportUsersCsv();

            assertThat(linesOf(csv)).doesNotContain("");
        }

        @Test
        @DisplayName("usa LF como separador de línea, sin retornos de carro")
        void usesLineFeedOnlyAsLineSeparator() {
            String csv = userService.exportUsersCsv();

            assertThat(csv).doesNotContain("\r");
        }
    }

    @Nested
    @DisplayName("Fila de datos")
    class DataRow {

        @Test
        @DisplayName("la columna 'id' de la primera fila vale '1'")
        void idFieldHasExpectedValue() {
            String csv = userService.exportUsersCsv();

            assertThat(fieldByColumnName(csv, "id")).isEqualTo("1");
        }

        @Test
        @DisplayName("la columna 'name' de la primera fila vale 'john'")
        void nameFieldHasExpectedValue() {
            String csv = userService.exportUsersCsv();

            assertThat(fieldByColumnName(csv, "name")).isEqualTo("john");
        }

        @Test
        @DisplayName("la columna 'email' de la primera fila vale 'john@test.com'")
        void emailFieldHasExpectedValue() {
            String csv = userService.exportUsersCsv();

            assertThat(fieldByColumnName(csv, "email")).isEqualTo("john@test.com");
        }

        @Test
        @DisplayName("ningún campo de la fila de datos viene vacío")
        void noDataFieldIsEmpty() {
            String csv = userService.exportUsersCsv();

            assertThat(fieldsOf(linesOf(csv).get(1)))
                    .hasSize(fieldsOf(linesOf(csv).get(0)).size())
                    .allSatisfy(field -> assertThat(field).isNotEmpty());
        }
    }

    @Nested
    @DisplayName("Determinismo")
    class Determinism {

        @Test
        @DisplayName("dos invocaciones sobre la misma instancia devuelven el mismo CSV")
        void twoInvocationsOnSameInstanceReturnSameCsv() {
            String first = userService.exportUsersCsv();
            String second = userService.exportUsersCsv();

            assertThat(second).isEqualTo(first);
        }

        @Test
        @DisplayName("el servicio no guarda estado: una instancia nueva devuelve el mismo CSV")
        void aFreshInstanceReturnsSameCsv() {
            String fromSharedInstance = userService.exportUsersCsv();
            String fromNewInstance = new UserService().exportUsersCsv();

            assertThat(fromNewInstance).isEqualTo(fromSharedInstance);
        }
    }
}
