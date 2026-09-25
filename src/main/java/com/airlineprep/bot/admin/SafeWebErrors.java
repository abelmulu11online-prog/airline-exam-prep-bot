package com.airlineprep.bot.admin;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponse;
@ControllerAdvice
public class SafeWebErrors {
 @ExceptionHandler(Exception.class)
 public ModelAndView error(Exception error) {
  int status=error instanceof ErrorResponse response?response.getStatusCode().value():
   error instanceof org.springframework.dao.DataAccessException||error instanceof org.springframework.transaction.TransactionException?503:
   error instanceof IllegalArgumentException?400:500;
  if(status>=500) org.slf4j.LoggerFactory.getLogger(getClass()).warn("Web request failed ({}); private details withheld",error.getClass().getSimpleName());
  var result=new ModelAndView("error");result.setStatus(HttpStatusCode.valueOf(status));result.addObject("status",status);return result;
 }
}
