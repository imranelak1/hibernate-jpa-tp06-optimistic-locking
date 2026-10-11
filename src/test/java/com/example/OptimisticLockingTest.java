package com.example;

import com.example.model.Reservation;
import com.example.service.*;
import com.example.util.Locking;
import javax.persistence.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OptimisticLockingTest {
    private EntityManagerFactory factory;
    private ReservationServiceImpl service;
    private final LocalDateTime start=LocalDateTime.of(2024,6,1,9,0);
    @BeforeEach void open() {
        factory=Persistence.createEntityManagerFactory("reservationPU",Map.of(
            "javax.persistence.jdbc.url","jdbc:h2:mem:locking_"+UUID.randomUUID(),"hibernate.show_sql","false"));
        service=new ReservationServiceImpl(factory);
    }
    @AfterEach void close() { service.close(); factory.close(); }
    private Reservation create() { return service.creerReservation(new Reservation("Salle A",start,start.plusHours(2))); }
    @Test void createsLoadsListsAndIncrementsVersionOnUpdate() {
        Reservation original=create(); assertNotNull(original.getId()); assertEquals(0,original.getVersion());
        Reservation snapshot=service.trouverParId(original.getId());
        assertNotSame(original,snapshot); snapshot.setSalle("Salle B");
        Reservation updated=service.modifierReservation(snapshot);
        assertEquals(1,updated.getVersion()); assertEquals("Salle B",service.trouverParId(original.getId()).getSalle());
        assertEquals(1,service.listerReservations().size());
    }
    @Test void twoIndependentEntityManagersRejectSecondWriterAndRollback() {
        Reservation initial=create();
        var result=ConcurrentReservationSimulator.simulate(factory,initial.getId());
        assertEquals(0,result.initialVersion()); assertEquals(1,result.winnerVersion());
        assertTrue(result.conflictDetected()); assertTrue(result.rolledBack());
        Reservation stored=service.trouverParId(initial.getId());
        assertEquals("Salle B",stored.getSalle()); assertEquals(1,stored.getVersion());
    }
    @Test void staleDetachedSnapshotCannotOverwriteWinnerAndServiceRecovers() {
        Reservation initial=create();
        Reservation first=service.trouverParId(initial.getId()), stale=service.trouverParId(initial.getId());
        first.setSalle("Salle B"); service.modifierReservation(first);
        stale.setSalle("Salle C");
        assertThrows(OptimisticLockException.class, () -> service.modifierReservation(stale));
        Reservation stored=service.trouverParId(initial.getId());
        assertEquals("Salle B",stored.getSalle()); assertEquals(1,stored.getVersion());
        stored.setSalle("Salle D");
        assertEquals(2,service.modifierReservation(stored).getVersion());
        assertEquals("Salle D",service.trouverParId(initial.getId()).getSalle());
    }
    @Test void retryReloadsVersionAndPreservesRequestedRoomAndDates() {
        Reservation initial=create();
        Reservation winner=service.trouverParId(initial.getId()), stale=service.trouverParId(initial.getId());
        winner.setSalle("Salle B"); service.modifierReservation(winner);
        stale.setSalle("Salle C"); stale.setDateDebut(start.plusMinutes(15)); stale.setDateFin(start.plusHours(3));
        Reservation updated=new ReservationRetryHandler(service).modifierAvecRetry(stale);
        assertEquals(2,updated.getVersion());
        Reservation stored=service.trouverParId(initial.getId());
        assertEquals("Salle C",stored.getSalle());
        assertEquals(start.plusMinutes(15),stored.getDateDebut()); assertEquals(start.plusHours(3),stored.getDateFin());
    }
    @Test void retryStopsAtConfiguredBoundAndPropagatesConflict() {
        Reservation initial=create(); int[] writes={0}, reads={0};
        ReservationService blocked=new ReservationService() {
            public Reservation creerReservation(Reservation r) { return service.creerReservation(r); }
            public Reservation modifierReservation(Reservation r) { writes[0]++; throw new OptimisticLockException("conflit impose"); }
            public Reservation trouverParId(Long id) { reads[0]++; return service.trouverParId(id); }
            public List<Reservation> listerReservations() { return service.listerReservations(); }
        };
        var error=assertThrows(OptimisticLockException.class,() -> new ReservationRetryHandler(blocked,3).modifierAvecRetry(initial));
        assertTrue(error.getMessage().contains("3 tentatives")); assertEquals(3,writes[0]); assertEquals(2,reads[0]);
        assertEquals(0,service.trouverParId(initial.getId()).getVersion());
    }
    @Test void retryFailsExplicitlyWhenReservationDisappears() {
        Reservation initial=create();
        ReservationService missing=new ReservationService() {
            public Reservation creerReservation(Reservation r) { return r; }
            public Reservation modifierReservation(Reservation r) { throw new OptimisticLockException("conflit impose"); }
            public Reservation trouverParId(Long id) { return null; }
            public List<Reservation> listerReservations() { return List.of(); }
        };
        assertThrows(IllegalArgumentException.class,() -> new ReservationRetryHandler(missing).modifierAvecRetry(initial));
    }
    @Test void validatesRequiredFieldsIntervalsAndRetryBound() {
        assertThrows(IllegalArgumentException.class, () -> service.creerReservation(new Reservation("",start,start.plusHours(1))));
        assertThrows(IllegalArgumentException.class, () -> service.creerReservation(new Reservation("A",start,start)));
        assertThrows(IllegalArgumentException.class, () -> service.creerReservation(new Reservation("A",null,start)));
        assertThrows(IllegalArgumentException.class, () -> service.modifierReservation(new Reservation("A",start,start.plusHours(1))));
        assertThrows(IllegalArgumentException.class, () -> new ReservationRetryHandler(service,0));
        assertTrue(service.listerReservations().isEmpty());
    }
    @Test void recognizesOptimisticExceptionWrappedByCommit() {
        var conflict=new OptimisticLockException("version obsolete");
        assertSame(conflict,Locking.optimisticCause(new RollbackException("commit echoue",conflict)));
        assertNull(Locking.optimisticCause(new IllegalArgumentException("erreur metier")));
    }
}
