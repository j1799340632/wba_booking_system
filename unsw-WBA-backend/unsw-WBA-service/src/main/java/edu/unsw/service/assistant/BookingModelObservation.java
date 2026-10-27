package edu.unsw.service.assistant;

/** Scoped to one observed API operation. Offline evals keep using the parser's separate lastUsage(). */
public final class BookingModelObservation implements AutoCloseable {
    private static final ThreadLocal<BookingModelObservation> CURRENT=new ThreadLocal<>();
    private final BookingModelObservation previous;
    private int calls;
    private String model;
    private boolean usageKnown;
    private long promptTokens,completionTokens,totalTokens;
    private BookingModelObservation() {previous=CURRENT.get();CURRENT.set(this);}
    public static BookingModelObservation begin() {return new BookingModelObservation();}
    public static void attempted(String name) {
        var current=CURRENT.get();if(current==null)return;
        current.calls++;current.model=name==null?"Unknown":name.substring(0,Math.min(80,name.length()));
        current.usageKnown=false;
    }
    public static void usage(long prompt,long completion,long total) {
        var current=CURRENT.get();if(current==null || current.calls!=1)return;
        current.usageKnown=true;current.promptTokens=prompt;current.completionTokens=completion;current.totalTokens=total;
    }
    public int calls() {return calls;}
    public String model() {return model;}
    public boolean usageKnown() {return calls==1 && usageKnown;}
    public long promptTokens() {return promptTokens;}
    public long completionTokens() {return completionTokens;}
    public long totalTokens() {return totalTokens;}
    @Override public void close() {if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
}
