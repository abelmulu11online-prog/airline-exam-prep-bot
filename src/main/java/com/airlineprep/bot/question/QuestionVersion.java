package com.airlineprep.bot.question;
import jakarta.persistence.*;
import com.airlineprep.bot.common.TimedEntity;
@Entity @Table(name="question_versions")
public class QuestionVersion extends TimedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 public Long getId() { return id; }

 Long questionId;
 public Long getQuestionId() { return questionId; }

 int versionNumber;
 public int getVersionNumber() { return versionNumber; }
 @Embedded
 QuestionContent content = new QuestionContent();
 public QuestionContent getContent() { return content; }

 String examName = "";
 public String getExamName() { return examName; }

 String categoryName = "";
 public String getCategoryName() { return categoryName; }

 String fingerprint;
 public String getFingerprint() { return fingerprint; }

 String stemFingerprint;
 public String getStemFingerprint() { return stemFingerprint; }

 String createdBy;
 public String getCreatedBy() { return createdBy; }

 String reviewedBy;
 public String getReviewedBy() { return reviewedBy; }

 java.time.Instant reviewedAt;
 public java.time.Instant getReviewedAt() { return reviewedAt; }

 java.time.Instant publishedAt;
 public java.time.Instant getPublishedAt() { return publishedAt; }

 @ElementCollection @org.hibernate.annotations.BatchSize(size=20) @CollectionTable(name="question_options",joinColumns=@JoinColumn(name="version_id"))
 @OrderColumn(name="position")
 java.util.List<AnswerOption> options = new java.util.ArrayList<>();
 public java.util.List<AnswerOption> getOptions() { return options; }
}
