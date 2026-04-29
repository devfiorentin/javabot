package fiorentin.dev.model;

public record Duelo(
        int id,
        long desafianteId,
        long desafiadoId,
        int perguntaId,
        int aposta,
        String respostaDesafiante,
        String respostaDesafiado,
        String status,
        Long vencedorId,
        Long chatId,      // ← novo
        Integer threadId  // ← novo
) {}