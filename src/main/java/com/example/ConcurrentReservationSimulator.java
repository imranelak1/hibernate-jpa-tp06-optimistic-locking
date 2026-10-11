package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationServiceImpl;
import com.example.util.Locking;
import javax.persistence.*;
import java.time.LocalDateTime;

public class ConcurrentReservationSimulator {
    public record ConflictResult(int initialVersion,int winnerVersion,boolean conflictDetected,boolean rolledBack) { }
    public static ConflictResult simulate(EntityManagerFactory factory,Long id) {
        EntityManager first=factory.createEntityManager(), second=factory.createEntityManager();
        try {
            first.getTransaction().begin(); second.getTransaction().begin();
            Reservation one=first.find(Reservation.class,id), two=second.find(Reservation.class,id);
            int initial=one.getVersion();
            System.out.println("Deux EntityManagers distincts lisent la version "+initial+" et "+two.getVersion());
            one.setSalle("Salle B"); first.getTransaction().commit();
            System.out.println("Transaction 1: commit Salle B, version="+one.getVersion());
            two.setSalle("Salle C");
            try {
                second.flush(); second.getTransaction().commit();
                throw new IllegalStateException("Un conflit optimiste etait attendu");
            } catch(RuntimeException error) {
                if(Locking.optimisticCause(error)==null) throw error;
                if(second.getTransaction().isActive()) second.getTransaction().rollback();
                System.out.println("Transaction 2: OptimisticLockException detectee; rollback termine");
                return new ConflictResult(initial,one.getVersion(),true,!second.getTransaction().isActive());
            }
        } finally {
            if(first.getTransaction().isActive()) first.getTransaction().rollback();
            if(second.getTransaction().isActive()) second.getTransaction().rollback();
            first.close(); second.close();
        }
    }
    public static void main(String[] args) {
        EntityManagerFactory factory=Persistence.createEntityManagerFactory("reservationPU");
        try {
            var service=new ReservationServiceImpl(factory);
            LocalDateTime start=LocalDateTime.of(2024,6,1,9,0);
            Reservation initial=service.creerReservation(new Reservation("Salle A",start,start.plusHours(2)));
            simulate(factory,initial.getId());
            Reservation stored=service.trouverParId(initial.getId());
            System.out.println("Apres conflit: "+stored.getSalle()+", version="+stored.getVersion());
        } finally { factory.close(); }
    }
}
