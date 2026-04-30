package com.example.kirk3;

import jakarta.persistence.*;

@Entity
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private String email;
    @Column(unique = true)
    private String username;
    private String passwordHash;
    private String verificationCode;
    private boolean verified = false;

    public UserAccount() {}
    public UserAccount(String email, String username, String passwordHash, String verificationCode) {
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.verificationCode = verificationCode;
    }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getVerificationCode() { return verificationCode; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
}