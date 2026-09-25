package com.airlineprep.bot.admin;
import java.security.Principal;
import jakarta.validation.Valid;
import com.airlineprep.bot.payment.*;
import com.airlineprep.bot.common.ExamException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
@Controller @RequestMapping("/admin/payment-methods")
public class PaymentMethodController {
 private final PaymentMethodService methods;
 public PaymentMethodController(PaymentMethodService m) {methods=m;}
 @GetMapping public String list(@RequestParam(defaultValue="0") int page,Model model) {
  model.addAttribute("rows",methods.list(false,page));model.addAttribute("page",Math.max(0,page));return "admin/payment-methods";
 }
 @GetMapping("/new") public String create(Model model) {
  model.addAttribute("form",new PaymentMethodService.Form("TELEBIRR","","","","",false,0,null));model.addAttribute("action","/admin/payment-methods/new");return "admin/payment-method-form";
 }
 @GetMapping("/{id}/edit") public String edit(@PathVariable long id,Model model) {
  model.addAttribute("form",methods.get(id).details());model.addAttribute("action","/admin/payment-methods/"+id+"/edit");return "admin/payment-method-form";
 }
 @PostMapping("/new") public String create(@Valid @ModelAttribute("form") PaymentMethodService.Form form,BindingResult errors,Model model,Principal admin) {return save(null,form,errors,model,admin);}
 @PostMapping("/{id}/edit") public String edit(@PathVariable long id,@Valid @ModelAttribute("form") PaymentMethodService.Form form,BindingResult errors,Model model,Principal admin) {return save(id,form,errors,model,admin);}
 private String save(Long id,PaymentMethodService.Form form,BindingResult errors,Model model,Principal admin) {
  if(!errors.hasErrors()) try {methods.save(id,form,admin.getName());} catch(ExamException e) {errors.reject("payment.invalid","This method changed. Reload and try again.");}
  if(errors.hasErrors()) {model.addAttribute("action",id==null?"/admin/payment-methods/new":"/admin/payment-methods/"+id+"/edit");return "admin/payment-method-form";}
  return "redirect:/admin/payment-methods?saved";
 }
}
