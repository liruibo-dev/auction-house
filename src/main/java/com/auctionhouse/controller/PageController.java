package com.auctionhouse.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/auctions")
    public String auctions() {
        return "auctions";
    }

    @GetMapping("/auctions/create")
    public String createAuction() {
        return "auction-create";
    }

    @GetMapping("/auctions/{id}")
    public String auctionDetail(@PathVariable Long id) {
        return "auction-detail";
    }

    @GetMapping("/my/auctions")
    public String myAuctions() {
        return "my-auctions";
    }
}
