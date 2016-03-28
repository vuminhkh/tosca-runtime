package com.toscaruntime.sdk.workflow;

import java.util.Collection;

public interface Listener {

    void onStop();

    void onCancel();

    void onFinish();

    void onFailure(Collection<Throwable> e);
}
