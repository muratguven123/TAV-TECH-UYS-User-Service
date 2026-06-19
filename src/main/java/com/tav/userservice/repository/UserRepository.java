package com.tav.userservice.repository;

import com.tav.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE u.id = :id")
    Optional<User> findByIdWithRoles(Long id);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE u.isActive = true")
    List<User> findAllActive();
}
