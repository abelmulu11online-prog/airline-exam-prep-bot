package com.airlineprep.bot.payment;
import java.util.*;
import com.airlineprep.bot.common.ExamException;
public record ReceiptMetadata(String fileId,String uniqueId,String type,String filename,String mime,long size) {
 public static final int MAX_BYTES=10*1024*1024;
 public ReceiptMetadata {
  if(fileId==null||!fileId.matches("[A-Za-z0-9_-]{1,1024}")||uniqueId==null||!uniqueId.matches("[A-Za-z0-9_-]{1,255}")
    ||size<1||size>MAX_BYTES) throw new ExamException("payment.receiptInvalid");
  if(type==null||mime==null||!List.of("PHOTO","DOCUMENT").contains(type)||!List.of("image/jpeg","image/png","application/pdf").contains(mime))
   throw new ExamException("payment.receiptInvalid");
  if("PHOTO".equals(type)) { filename="receipt.jpg"; if(!mime.equals("image/jpeg")) throw new ExamException("payment.receiptInvalid"); }
  else {
   if(filename==null||filename.length()>200||filename.chars().anyMatch(c->c<32||c==127||c=='/'||c==92))
    throw new ExamException("payment.receiptInvalid");
   String lower=filename.toLowerCase(Locale.ROOT);
   boolean matches=switch(mime) {case "image/jpeg" -> lower.endsWith(".jpg")||lower.endsWith(".jpeg");case "image/png" -> lower.endsWith(".png");default -> lower.endsWith(".pdf");};
   if(!matches) throw new ExamException("payment.receiptInvalid");
  }
 }
 public boolean matchesBytes(byte[] bytes) {
  if(bytes==null||bytes.length<5||bytes.length>MAX_BYTES) return false;
  return switch(mime) {
   case "image/jpeg" -> (bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255;
   case "image/png" -> bytes.length>=8&&Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10});
   case "application/pdf" -> new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
   default -> false;
  };
 }
}
