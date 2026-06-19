package com.tav.userservice.controller;

import com.tav.userservice.dto.UserCreateRequest;
import com.tav.userservice.dto.UserDto;
import com.tav.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "userCount", userService.getUserCount()
        ));
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> getActiveUsers() {
        return ResponseEntity.ok(userService.getAllActiveUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }
}
