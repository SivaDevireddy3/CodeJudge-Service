package com.siva.codejudge_service.enums;

public enum Verdict {
    AC,       // Accepted
    WA,       // Wrong Answer
    TLE,      // Time Limit Exceeded
    MLE,      // Memory Limit Exceeded
    RE,       // Runtime Error
    CE,       // Compile Error  ← added; was missing, judge engine was returning RE for compile errors
    PENDING,  // Not yet judged
    RUNNING   // Currently being judged
}