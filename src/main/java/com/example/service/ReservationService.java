package com.example.service;

import com.example.model.Reservation;
import java.util.List;

public interface ReservationService {
    Reservation creerReservation(Reservation reservation);
    Reservation modifierReservation(Reservation reservation);
    Reservation trouverParId(Long id);
    List<Reservation> listerReservations();
}
