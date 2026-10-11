package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationServiceImpl;
import javax.persistence.*;
import java.time.LocalDateTime;

public class ConcurrentReservationWithRetry {
    public static Reservation simulate(ReservationServiceImpl service) {
        LocalDateTime start=LocalDateTime.of(2024,6,2,9,0);
        Reservation initial=service.creerReservation(new Reservation("Salle A",start,start.plusHours(2)));
        Reservation first=service.trouverParId(initial.getId()), second=service.trouverParId(initial.getId());
        first.setSalle("Salle B");
        Reservation winner=service.modifierReservation(first);
        System.out.println("Snapshots detaches: gagnant Salle B version="+winner.getVersion()+"; snapshot obsolete version="+second.getVersion());
        second.setSalle("Salle C"); second.setDateFin(start.plusHours(3));
        Reservation retried=new ReservationRetryHandler(service).modifierAvecRetry(second);
        System.out.println("Retry reussi: "+retried.getSalle()+", fin="+retried.getDateFin()+", version="+retried.getVersion());
        return retried;
    }
    public static void main(String[] args) {
        try(var service=new ReservationServiceImpl()) { simulate(service); }
    }
}
