package com.example.demo.user;

import org.springframework.stereotype.Service;

@Service
public class UserService {

    public String exportUsersCsv() {
        return "id,name,email\n1,john,john@test.com";
    }
}
