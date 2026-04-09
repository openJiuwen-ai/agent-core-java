  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.workflow.components.flow.loop;
import com.openjiuwen.core.workflow.ComponentAbility;
import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.WorkflowComponent;
import java.util.ArrayList;
import java.util.List;
public class LoopGroup extends com.openjiuwen.core.workflow.component.loop.LoopGroup {
    public LoopGroup addWorkflowComp(String id,ComponentComposable c,Object in,Object sin,Boolean w,List ab){super.addWorkflowComp(id,c,w,in,null,sin,null,conv(ab));return this;}
    public LoopGroup addWorkflowComp(String id,Object c,Object in,Object sin,Boolean w,List ab){return addWorkflowComp(id,wrap(c),in,sin,w,ab);}
    public LoopGroup addWorkflowComp(String id,ComponentComposable c,Object in){super.addWorkflowComp(id,c,null,in,null,null,null,null);return this;}
    public LoopGroup addWorkflowComp(String id,Object c,Object in){super.addWorkflowComp(id,wrap(c),null,in,null,null,null,null);return this;}
    public LoopGroup addWorkflowComp(String id,ComponentComposable c){super.addWorkflowComp(id,c,null,null,null,null,null,null);return this;}
    public LoopGroup addWorkflowComp(String id,Object c){super.addWorkflowComp(id,wrap(c),null,null,null,null,null,null);return this;}
    private static ComponentComposable wrap(Object o){if(o instanceof ComponentComposable c)return c;return new WorkflowComponent(){public Object invoke(Object i,com.openjiuwen.core.session.NodeSessionApi s,com.openjiuwen.core.context.ModelContext cx){return i;}};}
    private static List conv(List a){if(a==null)return null;List r=new ArrayList();for(Object x:a)r.add(com.openjiuwen.core.workflow.component.ComponentAbility.valueOf(((ComponentAbility)x).name()));return r;}
}