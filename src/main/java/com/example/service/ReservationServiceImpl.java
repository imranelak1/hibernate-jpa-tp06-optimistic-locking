package com.example.service;

import com.example.model.Reservation;
import com.example.util.*;
import javax.persistence.*;
import java.util.*;

public class ReservationServiceImpl implements ReservationService,AutoCloseable {
    private final EntityManagerFactory factory;
    private final boolean ownsFactory;
    public ReservationServiceImpl() { this(Persistence.createEntityManagerFactory("reservationPU"),true); }
    public ReservationServiceImpl(EntityManagerFactory factory) { this(factory,false); }
    private ReservationServiceImpl(EntityManagerFactory factory,boolean ownsFactory) {
        this.factory=Objects.requireNonNull(factory); this.ownsFactory=ownsFactory;
    }
    @Override public Reservation creerReservation(Reservation reservation) {
        validate(reservation);
        if(reservation.getId()!=null) throw new IllegalArgumentException("Reservation deja persistante");
        return Jpa.transaction(factory,em -> { em.persist(reservation); return reservation; });
    }
    @Override public Reservation modifierReservation(Reservation reservation) {
        validate(reservation);
        if(reservation.getId()==null) throw new IllegalArgumentException("Identifiant requis");
        try {
            return Jpa.transaction(factory,em -> {
                if(em.find(Reservation.class,reservation.getId())==null) throw new IllegalArgumentException("Reservation non trouvee");
                // merge conserve la VERSION du snapshot detache et refuse une version obsolete.
                Reservation managed=em.merge(reservation);
                em.flush();
                return managed;
            });
        } catch(RuntimeException error) {
            OptimisticLockException optimistic=Locking.optimisticCause(error);
            if(optimistic!=null) throw optimistic;
            throw error;
        }
    }
    @Override public Reservation trouverParId(Long id) {
        return Jpa.transaction(factory,em -> em.find(Reservation.class,id));
    }
    @Override public List<Reservation> listerReservations() {
        return Jpa.transaction(factory,em -> em.createQuery("SELECT r FROM Reservation r ORDER BY r.id",Reservation.class).getResultList());
    }
    private static void validate(Reservation reservation) {
        Objects.requireNonNull(reservation,"Reservation requise");
        if(reservation.getSalle()==null || reservation.getSalle().isBlank()) throw new IllegalArgumentException("Salle requise");
        if(reservation.getDateDebut()==null || reservation.getDateFin()==null || !reservation.getDateDebut().isBefore(reservation.getDateFin()))
            throw new IllegalArgumentException("Creneau invalide");
    }
    @Override public void close() { if(ownsFactory) factory.close(); }
}
