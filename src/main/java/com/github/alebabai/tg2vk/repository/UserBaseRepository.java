package com.github.alebabai.tg2vk.repository;

import com.github.alebabai.tg2vk.domain.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserBaseRepository extends PagingAndSortingRepository<User, Integer> {
    @Query("SELECT u FROM User u  WHERE u.settings.id IN (SELECT s.id FROM UserSettings s WHERE s.started=true)")
    List<User> findAllStarted();

    Optional<User> findOneByVkId(@Param("vkId") Integer id);

    Optional<User> findOneByTgId(@Param("tgId") Integer id);
}
