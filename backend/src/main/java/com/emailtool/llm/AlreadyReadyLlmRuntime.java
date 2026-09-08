package com.emailtool.llm;

public final class AlreadyReadyLlmRuntime implements LlmRuntime {

    @Override
    public boolean ensureReady() {
        return true;
    }

    @Override
    public String lastError() {
        return null;
    }

    @Override
    public void releaseIfOwned() {}
}
