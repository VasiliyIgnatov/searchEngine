package searchengine.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller

public class DefaultController {


    /**
     * Метод формирует страницу из HTML-файла index2.html,
     * который находится в папке resources/templates.
     * Это делает библиотека Thymeleaf.
     */


    @RequestMapping("/")
    public String index() {
        return "index";
    }
 }
