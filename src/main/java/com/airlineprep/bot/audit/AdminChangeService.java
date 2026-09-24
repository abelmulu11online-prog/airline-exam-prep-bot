package com.airlineprep.bot.audit;

import org.springframework.stereotype.Service;

@Service
public class AdminChangeService {
    private final AdminChangeRepository changes;
    public AdminChangeService(AdminChangeRepository changes) { this.changes = changes; }
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void record(String actor, String action, String target, String before, String after) {
        AdminChange change = new AdminChange();
        change.setActor(actor); change.setAction(action); change.setTarget(target);
        change.setBeforeValue(before); change.setAfterValue(after);
        changes.save(change);
    }
}
