package com.airlineprep.bot.telegram;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class LocalizationIntegrityTests {
 Properties read(String name) throws Exception {var p=new Properties();try(var in=getClass().getResourceAsStream("/"+name);var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){p.load(reader);}return p;}
 Set<String> slots(String text) {var slots=new HashSet<String>();var matcher=Pattern.compile("\\{(\\d+)(?:[,}])").matcher(text);while(matcher.find())slots.add(matcher.group(1));return slots;}
 @Test void everyAmharicKeyAndPlaceholderMatchesEnglish() throws Exception {
  var en=read("messages.properties");var am=read("messages_am.properties");assertThat(am.stringPropertyNames()).isEqualTo(en.stringPropertyNames());
  for(String key:en.stringPropertyNames()) {assertThat(am.getProperty(key)).as(key).isNotBlank().doesNotContain("\uFFFD");assertThat(slots(am.getProperty(key))).as(key).isEqualTo(slots(en.getProperty(key)));}
 }
}
