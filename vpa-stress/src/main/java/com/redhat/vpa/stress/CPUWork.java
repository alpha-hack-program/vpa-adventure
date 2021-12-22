package com.redhat.vpa.stress;

public interface CPUWork {
    void doWork(Integer start, Integer end, Integer duration, Integer steps, Integer threads);
}

