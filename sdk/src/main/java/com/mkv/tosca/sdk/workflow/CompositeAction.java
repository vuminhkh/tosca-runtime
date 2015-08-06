package com.mkv.tosca.sdk.workflow;

import java.util.List;

/**
 * @author Minh Khang VU
 */
public abstract class CompositeAction implements Action {

    protected List<Action> actionList;

    public List<Action> getActionList() {
        return actionList;
    }

    public void setActionList(List<Action> actionList) {
        this.actionList = actionList;
    }
}
