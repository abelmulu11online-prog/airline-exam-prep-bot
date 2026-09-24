package com.airlineprep.bot.category;

import jakarta.persistence.*;
import com.airlineprep.bot.common.TimedEntity;

@Entity
@Table(name = "categories")
public class Category extends TimedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long examTypeId;

    private String code;

    private String name;

    private String nameAm;

    private boolean active;

    private int displayOrder;
    public Long getId() { return id; }
    public Long getExamTypeId() { return examTypeId; }
    public void setExamTypeId(Long value) { examTypeId = value; }
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getNameAm() { return nameAm; }
    public void setNameAm(String value) { nameAm = value; }
    public boolean getActive() { return active; }
    public void setActive(boolean value) { active = value; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int value) { displayOrder = value; }
}
