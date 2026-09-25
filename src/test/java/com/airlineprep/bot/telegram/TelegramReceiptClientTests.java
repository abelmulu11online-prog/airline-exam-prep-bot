package com.airlineprep.bot.telegram;
import java.io.*;
import java.net.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class TelegramReceiptClientTests {
 @Test @SuppressWarnings({"rawtypes","unchecked"})
 void downloadUsesStoredFileIdAndEnforcesStreamBound() throws Exception {
  HttpClient http=mock(HttpClient.class);HttpResponse metadata=mock(HttpResponse.class),download=mock(HttpResponse.class);
  when(metadata.statusCode()).thenReturn(200);when(metadata.body()).thenReturn("{\"ok\":true,\"result\":{\"file_path\":\"photos/file_1.jpg\",\"file_size\":10}}");
  when(download.statusCode()).thenReturn(200);when(download.body()).thenReturn(new ByteArrayInputStream(new byte[]{-1,-40,-1,0,0}));
  when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(metadata,download);
  var client=new TelegramBotClient(new TelegramBotProperties(true,"123456:unit-test-placeholder"),http,new ObjectMapper());
  assertThat(client.receiptBytes("stored_id")).hasSize(5);
  var requests=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);verify(http,times(2)).send(requests.capture(),any());
  assertThat(requests.getAllValues().get(1).uri().getHost()).isEqualTo("api.telegram.org");
  when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(metadata,download);
  when(download.body()).thenReturn(new ByteArrayInputStream(new byte[com.airlineprep.bot.payment.ReceiptMetadata.MAX_BYTES+1]));
  assertThatThrownBy(()->client.receiptBytes("stored_id")).isInstanceOf(TelegramBotClient.ApiException.class).hasNoCause();
 }
 @ParameterizedTest @ValueSource(strings={"../file.jpg","photos/../../private","https://evil.test/file","photos/file.jpg?token=x",""})
 @SuppressWarnings({"rawtypes","unchecked"})
 void unsafeTelegramFilePathsNeverReachDownload(String path) throws Exception {
  HttpClient http=mock(HttpClient.class);HttpResponse response=mock(HttpResponse.class);
  when(response.statusCode()).thenReturn(200);
  when(response.body()).thenReturn(new ObjectMapper().writeValueAsString(java.util.Map.of("ok",true,"result",java.util.Map.of("file_path",path,"file_size",10))));
  when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
  var client=new TelegramBotClient(new TelegramBotProperties(true,"123456:unit-test-placeholder"),http,new ObjectMapper());
  assertThatThrownBy(()->client.receiptBytes("stored")).isInstanceOf(TelegramBotClient.ApiException.class).hasNoCause();
  verify(http,times(1)).send(any(),any());
 }
}
