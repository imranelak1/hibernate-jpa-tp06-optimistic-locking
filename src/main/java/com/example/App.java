package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationServiceImpl;
import javax.persistence.*;
import java.time.LocalDateTime;

public class App {
    public static void main(String[] args) {
        EntityManagerFactory factory=Persistence.createEntityManagerFactory("reservationPU");
        try {
            var service=new ReservationServiceImpl(factory);
            LocalDateTime start=LocalDateTime.of(2024,6,1,9,0);
            Reservation initial=service.creerReservation(new Reservation("Salle A",start,start.plusHours(2)));
            System.out.println("=== TP06 - Verrouillage optimiste et retry ===");
            var conflict=ConcurrentReservationSimulator.simulate(factory,initial.getId());
            Reservation protectedRow=service.trouverParId(initial.getId());
            System.out.println("Lecture apres rollback: "+protectedRow.getSalle()+", version="+protectedRow.getVersion()+"; gagnant conserve");
            Reservation retried=ConcurrentReservationWithRetry.simulate(service);
            Reservation stored=service.trouverParId(retried.getId());
            if(!conflict.conflictDetected() || !conflict.rolledBack() || !"Salle B".equals(protectedRow.getSalle())
                    || !"Salle C".equals(stored.getSalle()) || stored.getVersion()!=2)
                throw new IllegalStateException("Verification du scenario echouee");
            System.out.println("Liste finale: "+service.listerReservations().size()+" reservations");
            System.out.println("RESULTAT: conflit reel detecte, rollback confirme, retry conserve Salle C et sa nouvelle date; version 0 -> 1 -> 2.");
        } finally { factory.close(); }
    }
}
