package com.airlineprep.bot.admin;

import jakarta.persistence.*;
import com.airlineprep.bot.common.TimedEntity;

@Entity
@Table(name = "admin_users")
public class AdminUser extends TimedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private String passwordHash;

    private boolean enabled;
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String value) { username = value; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String value) { passwordHash = value; }
    public boolean getEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
}
