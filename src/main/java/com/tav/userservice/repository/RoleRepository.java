package com.tav.userservice.repository;

import com.tav.userservice.entity.Role;
import com.tav.userservice.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByName(RoleName name);
}
