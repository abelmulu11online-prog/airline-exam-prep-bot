package com.airlineprep.bot.payment;
import com.airlineprep.bot.telegram.PaymentFlow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
class ReceiptTests {
 @ParameterizedTest @ValueSource(strings={"image/jpeg","image/png","application/pdf"})
 void supportedDocumentsValidateAndRequireMatchingBytes(String mime) {
  String ext=mime.equals("image/jpeg")?"jpg":mime.equals("image/png")?"png":"pdf";
  var r=new ReceiptMetadata("file","unique","DOCUMENT","test."+ext,mime,10);
  byte[] bytes=switch(ext) {case "jpg"->new byte[]{-1,-40,-1,0,0};case "png"->new byte[]{-119,80,78,71,13,10,26,10};default->"%PDF-".getBytes();};
  assertThat(r.matchesBytes(bytes)).isTrue();assertThat(r.matchesBytes("<html>bad</html>".getBytes())).isFalse();
 }
 @ParameterizedTest @ValueSource(strings={"test.exe","test.jpg.exe","../test.jpg","x\\test.jpg","test.html",""})
 void dangerousFilenameOrExtensionRejected(String name) {assertThatThrownBy(()->new ReceiptMetadata("file","unique","DOCUMENT",name,"image/jpeg",100)).hasMessage("payment.receiptInvalid");}
 @Test void sizeAndMissingMetadataRejected() {
  for(long size:new long[]{-1,0,10485761}) assertThatThrownBy(()->new ReceiptMetadata("file","unique","PHOTO",null,"image/jpeg",size)).hasMessage("payment.receiptInvalid");
  assertThatThrownBy(()->new ReceiptMetadata(null,"unique","PHOTO",null,"image/jpeg",10)).hasMessage("payment.receiptInvalid");
  assertThatThrownBy(()->new ReceiptMetadata("file",null,"PHOTO",null,"image/jpeg",10)).hasMessage("payment.receiptInvalid");
  assertThatThrownBy(()->new ReceiptMetadata("file","unique","DOCUMENT","test.jpg",null,10)).hasMessage("payment.receiptInvalid");
 }
 @Test void largestTelegramPhotoSelectedAndUnsupportedUpdatesRejected() throws Exception {
  var mapper=new ObjectMapper();
  var r=PaymentFlow.parseReceipt(mapper.readTree("{\"photo\":[{\"file_id\":\"small\",\"file_unique_id\":\"u1\",\"width\":10,\"height\":10,\"file_size\":20},{\"file_id\":\"large\",\"file_unique_id\":\"u2\",\"width\":100,\"height\":100,\"file_size\":200}]}"));
  assertThat(r.fileId()).isEqualTo("large");
  assertThatThrownBy(()->PaymentFlow.parseReceipt(mapper.readTree("{\"video\":{}}"))).hasMessage("payment.receiptInvalid");
 }
 @ParameterizedTest @ValueSource(strings={"/start","/help","a","a b c","a<script>","é123","abc\nxyz"})
 void malformedReferencesRejected(String ref) {assertThatThrownBy(()->ManualPaymentService.normalizeReference(ref)).hasMessage("payment.referenceInvalid");}
}
