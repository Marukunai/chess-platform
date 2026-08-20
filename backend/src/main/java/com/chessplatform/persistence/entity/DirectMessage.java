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
 * Un mensaje privado entre dos amigos — a diferencia del chat de partida (ChatMessage,
 * puramente de retransmisión, nunca se guarda), esto SÍ se persiste: tiene que poder
 * leerse aunque el destinatario estuviera desconectado cuando se mandó.
 */
@Entity
@Table(name = "direct_messages")
public class DirectMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recipient_id")
    private User recipient;

    @Column(nullable = false, length = 1000)
    private String text;

    @Column(nullable = false, updatable = false)
    private Instant sentAt = Instant.now();

    // Solo importa desde el punto de vista del destinatario — quien envía un mensaje ya
    // lo ha "leído" por definición, no hace falta trackearlo para el propio sender.
    // false por defecto: recién creado, el destinatario todavía no lo ha visto.
    @Column(nullable = false)
    private boolean read = false;

    protected DirectMessage() {
        // JPA
    }

    public DirectMessage(User sender, User recipient, String text) {
        this.sender = sender;
        this.recipient = recipient;
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public User getSender() {
        return sender;
    }

    public User getRecipient() {
        return recipient;
    }

    public String getText() {
        return text;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public boolean isRead() {
        return read;
    }

    public void markAsRead() {
        this.read = true;
    }
}