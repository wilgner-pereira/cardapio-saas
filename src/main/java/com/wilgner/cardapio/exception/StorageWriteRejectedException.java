package com.wilgner.cardapio.exception;

/** A completed HTTP 4xx response: the storage explicitly refused the new object. */
public class StorageWriteRejectedException extends ExternalServiceException {
    public StorageWriteRejectedException() { super("Falha ao enviar imagem para o storage"); }
}
