package com.chessplatform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Una relación de amistad, en cualquiera de sus dos estados. Se guarda con dirección
 * (requester -> addressee, quién la propuso a quién) aunque una vez aceptada la
 * relación en sí ya no tiene dirección — se conserva igualmente porque no cuesta nada y
 * podría ser útil más adelante (p. ej. "solicitudes que envié" separado de "amigos").
 *
 * Rechazar una solicitud BORRA la fila en vez de marcarla como "RECHAZADA" — no hace
 * falta guardar el historial de rechazos, y así la persona puede volver a intentarlo
 * más adelante sin que quede nada bloqueando un segundo intento.
 */
@Entity
@Table(name = "friendships")
public class Friendship {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "requester_id")
    private User requester;

    @ManyToOne(optional = false)
    @JoinColumn(name = "addressee_id")
    private User addressee;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Friendship() {
        // JPA
    }

    public Friendship(User requester, User addressee) {
        this.requester = requester;
        this.addressee = addressee;
        this.status = STATUS_PENDING;
    }

    public String getId() {
        return id;
    }

    public User getRequester() {
        return requester;
    }

    public User getAddressee() {
        return addressee;
    }

    public String getStatus() {
        return status;
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public void accept() {
        this.status = STATUS_ACCEPTED;
    }

    /** El otro lado de la amistad visto desde userId — evita repetir este if/else en cada sitio que lo necesita. */
    public User theOtherUser(String userId) {
        return requester.getId().equals(userId) ? addressee : requester;
    }
}