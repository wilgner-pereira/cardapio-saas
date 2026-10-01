package com.wilgner.cardapio.service;

public enum ImageUploadProfile {
    PRODUCT(512, 500 * 1024),
    HERO(1920, 1024 * 1024);

    private final int maxDimension;
    private final int maxBytes;

    ImageUploadProfile(int maxDimension, int maxBytes) {
        this.maxDimension = maxDimension;
        this.maxBytes = maxBytes;
    }

    public int maxBytes() { return maxBytes; }

    public void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || width > maxDimension || height > maxDimension
                || (long) width * height > (long) maxDimension * maxDimension) {
            throw new IllegalArgumentException("Dimensões da imagem excedem o limite de " + maxDimension + " px por lado");
        }
    }

    public static ImageUploadProfile fromRequest(String type) {
        if ("product".equals(type)) return PRODUCT;
        if ("hero".equals(type)) return HERO;
        throw new IllegalArgumentException("Informe o tipo da imagem: product ou hero");
    }
}
