package com.airlineprep.bot.question;
import jakarta.persistence.*;
@Embeddable
public class AnswerOption {
 @Column(name="option_text") String text;
 boolean correct;
 protected AnswerOption() {}
 public AnswerOption(String text, boolean correct) { this.text=text; this.correct=correct; }
 public String getText() { return text; }
 public boolean getCorrect() { return correct; }
}
