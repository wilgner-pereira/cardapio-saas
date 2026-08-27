package com.wilgner.cardapio.model.entity;

import com.wilgner.cardapio.exception.BusinessException;

import java.util.Arrays;

public enum CardapioTema {
    ARTESANAL("artesanal"),
    BRASA("brasa"),
    ATLANTICO("atlantico"),
    VINHO("vinho"),
    GRAFITE("grafite");

    private final String valor;

    CardapioTema(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }

    public static CardapioTema fromValor(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new BusinessException("Tema não pode ser vazio");
        }
        return Arrays.stream(values())
                .filter(t -> t.valor.equalsIgnoreCase(valor.trim()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Tema inválido: '" + valor + "'. Valores aceitos: " +
                        String.join(", ", Arrays.stream(values()).map(CardapioTema::getValor).toList())
                ));
    }
}
