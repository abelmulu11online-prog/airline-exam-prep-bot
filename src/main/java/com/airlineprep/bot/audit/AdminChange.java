package com.airlineprep.bot.audit;

import jakarta.persistence.*;
import com.airlineprep.bot.common.TimedEntity;

@Entity
@Table(name = "admin_changes")
public class AdminChange extends TimedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String actor;

    private String action;

    private String target;
    @Column(columnDefinition = "text")
    private String beforeValue;
    @Column(columnDefinition = "text")
    private String afterValue;
    public Long getId() { return id; }
    public String getActor() { return actor; }
    public void setActor(String value) { actor = value; }
    public String getAction() { return action; }
    public void setAction(String value) { action = value; }
    public String getTarget() { return target; }
    public void setTarget(String value) { target = value; }
    public String getBeforeValue() { return beforeValue; }
    public void setBeforeValue(String value) { beforeValue = value; }
    public String getAfterValue() { return afterValue; }
    public void setAfterValue(String value) { afterValue = value; }
}
