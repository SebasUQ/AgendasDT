package co.edu.uniquindio.AgendasDT.dto;

import co.edu.uniquindio.AgendasDT.domain.enums.UserRol;

public record AuthResponse(String token, UserRol rol, String nombre, String email, Long ID, String phone) { }
