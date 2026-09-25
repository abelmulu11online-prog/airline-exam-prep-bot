package com.airlineprep.bot.engine;
import com.airlineprep.bot.mock.*;
import com.airlineprep.bot.practice.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @Transactional
class PerformanceSanityTests extends EngineFixture {
 @Autowired PracticeService practice;@Autowired MockAttemptService mocks;@Autowired JdbcTemplate jdbc;
 @Test void largerPublishedPoolSelectsOnePracticeAndFiftyUniqueFrozenItems() {
  prepareFixture(50);content(300);
  var delivery=practice.next(sender,null,null,false);assertThat(delivery.question()).isNotNull();
  long id=mocks.prepare(sender,"large-pool").attempt().id();
  assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT question_id) FROM mock_items WHERE attempt_id=?",Integer.class,id)).isEqualTo(50);
  mocks.open(sender,id,0,false);mocks.answer(sender,id,0,0,0);assertThat(mocks.submit(sender,id).score().unanswered()).isEqualTo(49);
  assertThat(questions.search("",null,null,null,null,null,0,"updatedAt").getContent()).hasSize(20);
 }
}
