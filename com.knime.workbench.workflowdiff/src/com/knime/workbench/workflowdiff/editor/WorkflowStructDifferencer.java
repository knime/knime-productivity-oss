package com.knime.workbench.workflowdiff.editor;

import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;

public class WorkflowStructDifferencer extends Differencer {

	
	@Override
    protected Object visit(final Object parent, final int description, final Object ancestor,
        final Object left, final Object right) {
    	if (description == CHANGE) {
    		if (super.contentsEqual(left, right)) {
    			return new FlowDiffNode((IDiffContainer)parent, PSEUDO_CONFLICT, (ITypedElement)ancestor,
                        (ITypedElement)left, (ITypedElement)right);
    		}
    	}
        return new FlowDiffNode((IDiffContainer)parent, description, (ITypedElement)ancestor,
            (ITypedElement)left, (ITypedElement)right);
    }
    @Override
    protected boolean contentsEqual(Object input1, Object input2) {
    	// we want all nodes to show up in the diff structure
    	return false;
    }
    
    @Override
    protected Object[] getChildren(Object input) {
    	Object[] children = super.getChildren(input);
    	if (children != null) {
    		return children;
    	} else {
    		return new Object[0];
//    		return null;
    	} 
    }
    
}
