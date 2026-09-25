package com.airlineprep.bot.question;
import jakarta.persistence.*;
import com.airlineprep.bot.common.TimedEntity;
@Entity @Table(name="question_import_batches")
public class ImportBatch extends TimedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 public Long getId() { return id; }

 String filename;
 public String getFilename() { return filename; }

 String fileType;
 public String getFileType() { return fileType; }

 String actor;
 public String getActor() { return actor; }
 @Enumerated(EnumType.STRING)
 ImportStatus status;
 public ImportStatus getStatus() { return status; }

 int totalRows;
 public int getTotalRows() { return totalRows; }

 int validRows;
 public int getValidRows() { return validRows; }

 int invalidRows;
 public int getInvalidRows() { return invalidRows; }

 int duplicateRows;
 public int getDuplicateRows() { return duplicateRows; }

 int importedRows;
 public int getImportedRows() { return importedRows; }

 int failedRows;
 public int getFailedRows() { return failedRows; }

 java.time.Instant completedAt;
 public java.time.Instant getCompletedAt() { return completedAt; }

}
