package fiorentin.dev.model;

public record Usuario(
        long id,
        String username,
        String nome,
        int xp,
        int moedas,
        int nivel
) {}