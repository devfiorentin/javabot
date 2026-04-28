package fiorentin.dev.model;

public record Licao(
        int id,
        String titulo,
        String conteudo,
        String dificuldade,
        int xpRecompensa
) {}