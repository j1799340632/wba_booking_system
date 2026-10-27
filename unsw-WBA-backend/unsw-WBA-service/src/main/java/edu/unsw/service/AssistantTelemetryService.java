package edu.unsw.service;

import edu.unsw.dao.mapper.AssistantEventMapper;
import edu.unsw.domain.entity.AssistantEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AssistantTelemetryService {
    private final AssistantEventMapper events;
    private final AtomicLong dropped=new AtomicLong();
    public AssistantTelemetryService(AssistantEventMapper events) {this.events=events;}
    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public void record(AssistantEvent event) {if(events.insert(event)!=1)throw new IllegalStateException("Observation was not saved");}
    public void dropped() {dropped.incrementAndGet();}
    public long droppedSinceRestart() {return dropped.get();}
}
