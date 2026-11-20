package com.smartcart.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="users")
public class UserEntity {
    @Id public String id;
    @Column(nullable=false, length=80) public String name;
    @Column(nullable=false, unique=true, length=254) public String email;
    @Column(name="password_hash", nullable=false, length=100) public String passwordHash;
    @Column(nullable=false, length=16) public String role="customer";
    @Column(name="sample_user", nullable=false) public boolean sampleUser;
    @Column(name="session_version", nullable=false) public int sessionVersion;
    @Column(name="created_at", nullable=false) public Instant createdAt=Instant.now();
    @Column(name="updated_at", nullable=false) public Instant updatedAt=Instant.now();
    public UserEntity() {}
}
