package com.airlineprep.bot.common;
public class ExamException extends RuntimeException {
 private final String key;
 public ExamException(String key) { super(key);this.key=key; }
 public String key() { return key; }
}
