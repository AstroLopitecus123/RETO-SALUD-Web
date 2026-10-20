package com.clinica.real.madrid.backend_citas.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BadRequestException.class)
	@SuppressWarnings("java:S4507")
	public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
	    System.err.println(ex.getMessage()); 

	    String msg = ex.getMessage();
	    String field = "";

	    if(msg.contains("correo")) field = "correo";
	    else if(msg.contains("DNI")) field = "dni";
	    else if(msg.contains("teléfono")) field = "telefono";

	    Map<String, Object> body = new HashMap<>();
	    body.put("timestamp", LocalDateTime.now(java.time.ZoneId.systemDefault()));
	    body.put("status", HttpStatus.CONFLICT.value()); 
	    body.put("error", "Conflict");
	    body.put("message", msg);
	    body.put("field", field); 

	    return new ResponseEntity<>(body, HttpStatus.CONFLICT);
	}

    @ExceptionHandler(HorarioNoDisponibleException.class)
    @SuppressWarnings("java:S4507")
    public ResponseEntity<Map<String, Object>> handleHorarioNoDisponible(HorarioNoDisponibleException ex) {
        System.err.println(ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now(java.time.ZoneId.systemDefault()));
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Conflict");
        body.put("code", "HORARIO_NO_DISPONIBLE");
        body.put("message", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @SuppressWarnings("java:S4507")
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        System.err.println(ex.getMessage()); 
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now(java.time.ZoneId.systemDefault()));
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", "Not Found");
        body.put("message", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    @SuppressWarnings("java:S4507")
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        System.err.println(ex.getMessage()); 
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now(java.time.ZoneId.systemDefault()));
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}

