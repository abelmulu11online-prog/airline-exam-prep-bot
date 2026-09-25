package com.airlineprep.bot.question;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
class ImportAttackTests {
 final QuestionFileParser parser=new QuestionFileParser();
 byte[] csv(String text) {return (String.join(",",QuestionFileParser.HEADERS)+"\r\n"+text).getBytes(StandardCharsets.UTF_8);}
 @ParameterizedTest @ValueSource(strings={"image/png","application/x-msdownload","text/html","application/pdf"})
 void contradictoryMimeIsRejected(String mime) {
  assertThatThrownBy(()->parser.parse(new MockMultipartFile("file","test.csv",mime,csv("x")))).hasMessageContaining("file type");
 }
 @ParameterizedTest @ValueSource(strings={"../../evil.xlsx","..\\evil.csv","bad\n.csv","evil.exe"})
 void unsafeNamesNeverBecomeFilesystemPaths(String name) {assertThatThrownBy(()->parser.parse(new MockMultipartFile("file",name,null,new byte[]{1}))).isInstanceOf(IllegalArgumentException.class);}
 @Test void hugeQuotedCellIsBoundedAndMalformedQuotesRejected() {
  var result=parser.parse(new MockMultipartFile("file","long.csv","text/csv",csv("\""+"x".repeat(13000)+"\"")));
  assertThat(result.rows().getFirst().error()).contains("too long");
  assertThatThrownBy(()->parser.parse(new MockMultipartFile("file","bad.csv","text/csv",csv("\"unterminated")))).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void caseVariantHeadersAndRenamedExecutableRejected() {
  byte[] headers=new String(csv("x"),StandardCharsets.UTF_8).replace("exam_type","EXAM_TYPE").getBytes(StandardCharsets.UTF_8);
  assertThatThrownBy(()->parser.parse(new MockMultipartFile("file","bad.csv","text/csv",headers))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->parser.parse(new MockMultipartFile("file","bad.csv","text/csv",new byte[]{'M','Z',0,1}))).isInstanceOf(IllegalArgumentException.class);
 }
 @ParameterizedTest @ValueSource(strings={"../evil.xml","/evil.xml","xl/../evil.xml","xl\\evil.xml","xl/externalLinks/externalLink1.xml","xl/embeddings/object.bin","xl/vbaProject.bin"})
 void hostileWorkbookPartsRejectedBeforePoi(String entry) throws Exception {
  var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry(entry));zip.write(new byte[]{1});zip.closeEntry();}
  assertThatThrownBy(()->parser.parse(new MockMultipartFile("file","bad.xlsx","application/octet-stream",bytes.toByteArray()))).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void expandedZipAndPartCountHaveExplicitLimits() throws Exception {
  var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("xl/large.xml"));byte[] block=new byte[1024];for(int i=0;i<21*1024;i++)zip.write(block);zip.closeEntry();}
  assertThatThrownBy(()->parser.parse(new MockMultipartFile("file","bad.xlsx",null,bytes.toByteArray()))).hasMessageContaining("20 MiB");
  var parts=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(parts)){for(int i=0;i<1001;i++){zip.putNextEntry(new ZipEntry("part"+i));zip.closeEntry();}}
  assertThatThrownBy(()->parser.parse(new MockMultipartFile("file","bad.xlsx",null,parts.toByteArray()))).hasMessageContaining("too many parts");
 }
 @Test void unicodeCsvAndModeratelyLargeXlsxRemainBounded() throws Exception {
  String row="";var values=new ArrayList<>(Collections.nCopies(QuestionFileParser.HEADERS.size(),""));values.set(2,"የሙከራ ጥያቄ");row=String.join(",",values);
  assertThat(parser.parse(new MockMultipartFile("file","unicode.csv","text/csv",csv(row))).rows().getFirst().values().get("question")).isEqualTo("የሙከራ ጥያቄ");
  byte[] bytes;try(var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {var sheet=workbook.createSheet("Questions");var header=sheet.createRow(0);for(int c=0;c<QuestionFileParser.HEADERS.size();c++)header.createCell(c).setCellValue(QuestionFileParser.HEADERS.get(c));for(int r=1;r<=500;r++)sheet.createRow(r).createCell(2).setCellValue("የሙከራ ጥያቄ "+r);workbook.write(out);bytes=out.toByteArray();}
  assertThat(parser.parse(new MockMultipartFile("file","unicode.xlsx",null,bytes)).rows()).hasSize(500);
 }
}
