package com.example.bot.model;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface GameRepository extends CrudRepository<Game,Long> {
        @Query(name = "Game.findByUsername")
        List<Game> findByUsername(@Param("contact") String contact);
}
