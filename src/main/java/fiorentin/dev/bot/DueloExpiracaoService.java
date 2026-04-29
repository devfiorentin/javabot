package fiorentin.dev.bot;

import fiorentin.dev.db.DueloDAO;
import fiorentin.dev.db.UsuarioDAO;
import fiorentin.dev.model.Duelo;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class DueloExpiracaoService {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final BiConsumer<Long, String> notificar;

    public DueloExpiracaoService(BiConsumer<Long, String> notificar) {
        this.notificar = notificar;
    }

    public void iniciar() {
        // Roda a cada 5 minutos verificando duelos expirados
        scheduler.scheduleAtFixedRate(this::verificarExpirados, 1, 5, TimeUnit.MINUTES);
        System.out.println("✅ Serviço de expiração de duelos iniciado!");
    }

    private void verificarExpirados() {
        List<Duelo> expirados = DueloDAO.buscarExpirados();

        for (Duelo duelo : expirados) {
            // Devolve as moedas pro desafiante
            UsuarioDAO.adicionarMoedas(duelo.desafianteId(), duelo.aposta());
            DueloDAO.cancelar(duelo.id());

            // Notifica o desafiante
            notificar.accept(duelo.desafianteId(),
                    "⏰ Seu duelo expirou! As <b>%d moedas</b> foram devolvidas."
                            .formatted(duelo.aposta()));

            System.out.println("⏰ Duelo " + duelo.id() + " expirado — moedas devolvidas.");
        }
    }

    public void parar() {
        scheduler.shutdown();
    }
}