package fiorentin.dev.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

public class Teclado {

    // Teclado do menu principal
    public static InlineKeyboardMarkup menuPrincipal() {
        return montar(
                List.of(
                        botao("📚 Aprender", "cmd:aprender"),
                        botao("👤 Perfil",   "cmd:perfil")
                ),
                List.of(
                        botao("🏆 Ranking",  "cmd:ranking"),
                        botao("⚔️ Duelos",   "cmd:duelos")
                ),
                List.of(
                        botao("🔗 Indicar amigos", "cmd:indicar"),
                        botao("🏪 Loja",           "cmd:loja")
                )
        );
    }

    public static InlineKeyboardMarkup loja() {
        return montar(
                List.of(
                        botao("🎰 Caixa Comum — 30 🪙",   "comprar:caixa_comum"),
                        botao("💎 Caixa Premium — 100 🪙", "comprar:caixa_premium")
                ),
                List.of(
                        botao("⚡ XP Duplo — 80 🪙",        "comprar:xp_duplo"),
                        botao("🛡️ Proteção — 50 🪙",        "comprar:protecao_duelo")
                ),
                List.of(
                        botao("🏠 Menu principal", "cmd:menu")
                )
        );
    }

    public static InlineKeyboardMarkup respostaDuelo(int dueloId) {
        return montar(
                List.of(
                        botao("A", "duelo_resp:%d:a".formatted(dueloId)),
                        botao("B", "duelo_resp:%d:b".formatted(dueloId)),
                        botao("C", "duelo_resp:%d:c".formatted(dueloId)),
                        botao("D", "duelo_resp:%d:d".formatted(dueloId))
                )
        );
    }


    // Teclado do quiz (A B C D)
    public static InlineKeyboardMarkup quiz(int perguntaId) {
        return montar(
                List.of(
                        botao("A", "quiz:%d:a".formatted(perguntaId)),
                        botao("B", "quiz:%d:b".formatted(perguntaId)),
                        botao("C", "quiz:%d:c".formatted(perguntaId)),
                        botao("D", "quiz:%d:d".formatted(perguntaId))
                )
        );
    }

    // Teclado de confirmação de duelo
    public static InlineKeyboardMarkup confirmarDuelo(int dueloId) {
        return montar(
                List.of(
                        botao("✅ Aceitar", "duelo_aceitar:%d".formatted(dueloId)),
                        botao("❌ Recusar", "duelo_recusar:%d".formatted(dueloId))
                )
        );
    }

    // Teclado voltar pro menu
    public static InlineKeyboardMarkup voltarMenu() {
        return montar(
                List.of(botao("🏠 Menu principal", "cmd:menu"))
        );
    }

    // ── Utilitários ──────────────────────────────────────────────────────────

    private static InlineKeyboardButton botao(String texto, String callback) {
        return InlineKeyboardButton.builder()
                .text(texto)
                .callbackData(callback)
                .build();
    }

    @SafeVarargs
    private static InlineKeyboardMarkup montar(List<InlineKeyboardButton>... linhas) {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(linhas))
                .build();
    }
}