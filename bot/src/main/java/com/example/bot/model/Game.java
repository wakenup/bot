package com.example.bot.model;

import org.hibernate.annotations.NamedQuery;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
@NamedQuery(
    name = "Game.findByUsername",
    query = "SELECT g FROM Game g WHERE g.contact = :contact"
)
public class Game {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String timeOfGame;

    private String cost;

    private String place;

    private String contact;

    private int players;

    private String comment;

    private Long typeOfGame;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTimeOfGame() {
        return timeOfGame;
    }

    public void setTimeOfGame(String timeOfGame) {
        this.timeOfGame = timeOfGame;
    }

    public String getCost() {
        return cost;
    }

    public void setCost(String cost) {
        this.cost = cost;
    }
    
    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place = place;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Long getTypeOfGame() {
        return typeOfGame;
    }

    public void setTypeOfGame(Long typeOfGame) {
        this.typeOfGame = typeOfGame;
    }

    public int getPlayers() {
        return players;
    }

    public void setPlayers(int players) {
        this.players = players;
    }
}
