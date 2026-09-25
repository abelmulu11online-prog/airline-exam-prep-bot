package com.airlineprep.bot.question;
import jakarta.persistence.*;
import com.airlineprep.bot.common.TimedEntity;
@Entity @Table(name="questions")
public class Question extends TimedEntity {
 long contentRevision;
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 public Long getId() { return id; }
 @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="current_version_id")
 QuestionVersion currentVersion;
 public QuestionVersion getCurrentVersion() { return currentVersion; }
 @Enumerated(EnumType.STRING)
 QuestionStatus status = QuestionStatus.DRAFT;
 public QuestionStatus getStatus() { return status; }
 @Version
 long revision;
 public long getRevision() { return revision; }

 String createdBy;
 public String getCreatedBy() { return createdBy; }

 String updatedBy;
 public String getUpdatedBy() { return updatedBy; }

}
