package fiorentin.dev.model;

public record LicaoProgresso(
        int id,
        String titulo,
        String dificuldade,
        boolean concluida
) {}