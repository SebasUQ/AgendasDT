package co.edu.uniquindio.AgendasDT.controller;

import co.edu.uniquindio.AgendasDT.dto.*;
import co.edu.uniquindio.AgendasDT.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('Administrador')")
public class UserController {
    private final UserService service;

    public UserController(UserService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        UserResponse user = service.create(request);
        return ResponseEntity.created(URI.create("/api/users/" + user.id())).body(user);
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return service.update(id, request);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) { return service.get(id); }
}
