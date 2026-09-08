package com.emailtool.llm;

public interface LlmRuntime {

    boolean ensureReady();

    String lastError();

    void releaseIfOwned();
}
