package com.example.dmnsidecar.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Redirige la raiz "/" a Swagger UI para que la documentacion sea visible
 * sin tener que conocer la ruta exacta.
 */
@RestController
public class HomeController {

    @GetMapping("/")
    public RedirectView home() {
        return new RedirectView("/swagger-ui/index.html");
    }
}
