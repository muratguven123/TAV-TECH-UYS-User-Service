package com.tav.userservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", length = 20, nullable = false, unique = true)
    private RoleName name;

    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    private Set<UserRole> userRoles = new HashSet<>();
}
