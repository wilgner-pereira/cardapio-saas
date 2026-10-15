package com.wilgner.cardapio.exception;

import com.wilgner.cardapio.model.dto.error.ApiErrorDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleConstraintViolation_sanitizesErrorAndFormatsFieldErrors() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        given(path.toString()).willReturn("atualizarLogo.logoUrl");
        given(violation.getPropertyPath()).willReturn(path);
        given(violation.getMessage()).willReturn("URL da logo inválida");

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));
        HttpServletRequest request = new MockHttpServletRequest("PUT", "/painel/estabelecimento/logo");

        ResponseEntity<ApiErrorDTO> response = handler.handleConstraintViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiErrorDTO body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo("Dados inválidos");
        assertThat(body.fieldErrors()).containsEntry("logoUrl", "URL da logo inválida");
        assertThat(body.message()).doesNotContain("atualizarLogo");
    }
}
