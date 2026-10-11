package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationService;
import javax.persistence.OptimisticLockException;
import java.time.LocalDateTime;
import java.util.Objects;

public class ReservationRetryHandler {
    private final ReservationService service;
    private final int maxAttempts;
    public ReservationRetryHandler(ReservationService service) { this(service,3); }
    public ReservationRetryHandler(ReservationService service,int maxAttempts) {
        if(maxAttempts<1) throw new IllegalArgumentException("Au moins une tentative requise");
        this.service=Objects.requireNonNull(service); this.maxAttempts=maxAttempts;
    }
    public Reservation modifierAvecRetry(Reservation reservation) {
        // Sauvegarder l'intention avant rechargement: le retry ne doit pas perdre Salle C.
        String desiredRoom=reservation.getSalle();
        LocalDateTime desiredStart=reservation.getDateDebut(), desiredEnd=reservation.getDateFin();
        Reservation candidate=reservation;
        for(int attempt=1;attempt<=maxAttempts;attempt++) {
            try { return service.modifierReservation(candidate); }
            catch(OptimisticLockException error) {
                if(attempt==maxAttempts) throw new OptimisticLockException("Echec apres "+maxAttempts+" tentatives",error);
                System.out.println("Conflit detecte: rechargement et retry "+attempt+"/"+(maxAttempts-1));
                candidate=service.trouverParId(reservation.getId());
                if(candidate==null) throw new IllegalArgumentException("Reservation supprimee pendant le retry");
                candidate.setSalle(desiredRoom); candidate.setDateDebut(desiredStart); candidate.setDateFin(desiredEnd);
            }
        }
        throw new IllegalStateException("Boucle de retry terminee sans resultat");
    }
}
