package com.mkv.tosca.sdk.workflow;

import java.util.List;

import com.google.common.collect.Lists;

/**
 * @author Minh Khang VU
 */
public abstract class CompositeAction implements Action {

    protected List<Action> actionList = Lists.newArrayList();

    public List<Action> getActionList() {
        return actionList;
    }

    public void setActionList(List<Action> actionList) {
        this.actionList = actionList;
    }
}
