package fiorentin.dev.model;

public record Pergunta(
        int id,
        int licaoId,
        String enunciado,
        String opcaoA,
        String opcaoB,
        String opcaoC,
        String opcaoD,
        String respostaCorreta,
        int xpRecompensa
) {}