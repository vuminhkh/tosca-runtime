package com.toscaruntime.sdk.workflow;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Minh Khang VU
 */
public abstract class CompositeAction implements Action {

    protected List<Action> actionList = new ArrayList<>();

    public List<Action> getActionList() {
        return actionList;
    }

    public void setActionList(List<Action> actionList) {
        this.actionList = actionList;
    }
}
