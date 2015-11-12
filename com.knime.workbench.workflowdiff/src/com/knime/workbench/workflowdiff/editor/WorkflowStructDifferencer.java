package com.knime.workbench.workflowdiff.editor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.core.runtime.IProgressMonitor;
import org.knime.core.node.workflow.WorkflowManager;

import com.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowContainer;
import com.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowNode;
import com.knime.workbench.workflowdiff.editor.FlowStructureCreator.FlowWorkflow;

import workflowmatching.WorkflowTree;

public class WorkflowStructDifferencer extends Differencer {

	private static class Node{
		private List<Node> fChildren;
		private int fCode;
		private Object fAncestor;
		private Object fLeft;
		private Object fRight;

		Node() {
			// nothing to do
		}

		Node(Node parent, Object ancestor, Object left, Object right) {
			parent.add(this);
			fAncestor = ancestor;
			fLeft = left;
			fRight = right;
		}

		void add(Node child) {
			if (fChildren == null)
				fChildren = new ArrayList<Node>();
			fChildren.add(child);
		}

		Object visit(WorkflowStructDifferencer d, Object parent, int level) {
			if (fCode == NO_CHANGE)
				return null;
			// dump(level);
			Object data = d.visit(parent, fCode, fAncestor, fLeft, fRight);
			if (fChildren != null) {
				Iterator<Node> i = fChildren.iterator();
				while (i.hasNext()) {
					Node n = (Node) i.next();
					n.visit(d, data, level + 1);
				}
			}
			return data;
		}

		@Override
		public String toString() {
			return "Node[ " + fLeft.toString() + " | " + fRight.toString() + " ]";
		}
	}

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
    
	@Override
	public Object findDifferences(boolean threeWay, IProgressMonitor pm, Object data, Object ancestor, Object left,
			Object right) {
		if (left instanceof FlowContainer && right instanceof FlowContainer) {
			FlowContainer leftFlowElement = (FlowContainer) left;
			FlowContainer rightFlowElement = (FlowContainer) right;
			WorkflowManager leftWorkflowManager = leftFlowElement.getWorkflowManager();
			WorkflowManager rightWorkflowManager = rightFlowElement.getWorkflowManager();
			WorkflowTree leftWorkflowTree = new WorkflowTree(leftWorkflowManager);
			WorkflowTree rightWorkflowTree = new WorkflowTree(rightWorkflowManager);
			Node root = new Node();
			List<List<WorkflowTree.Node>> leftSequence = leftWorkflowTree.toSequences();
			List<List<WorkflowTree.Node>> rightSequence = rightWorkflowTree.toSequences();
			Node workflownode = new Node(root, null, new FlowWorkflow(leftWorkflowManager),
					new FlowWorkflow(rightWorkflowManager));
			workflownode.fCode = CHANGE;

			sequentialAlignment(leftSequence, rightSequence, workflownode);
			List<Node> l = root.fChildren;
			if (l.size() > 0) {
				Node first = (Node) l.get(0);
				return first.visit(this, (FlowDiffNode) data, 0);
			}
		}
		return null;
	}

	private void sequentialAlignment(List<List<WorkflowTree.Node>> left, List<List<WorkflowTree.Node>> right,
			Node parent) {
		while (left.size() > 0) {
			double maxAlignment = 0;
			int best = -1;
			List<WorkflowTree.Node> sequence = left.remove(0);
			maxAlignment = calcAlignment(sequence, new ArrayList<WorkflowTree.Node>())
					+ sequentialAlignment(left, right);
			for (int i = 0; i < right.size(); i++) {
				List<WorkflowTree.Node> other = right.remove(i);
				double alignment = calcAlignment(sequence, other) + sequentialAlignment(left, right);
				if (alignment < maxAlignment) {
					maxAlignment = alignment;
					best = i;
				}
				right.add(i, other);
			}
			if (best >= 0) {
				needlemanWunsch(sequence, right.remove(best), parent);
			} else {
				needlemanWunsch(sequence, new ArrayList<WorkflowTree.Node>(), parent);
			}
		}
		while (right.size() > 0) {
			needlemanWunsch(new ArrayList<WorkflowTree.Node>(), right.remove(0), parent);
		}
	}

	private double sequentialAlignment(List<List<WorkflowTree.Node>> left, List<List<WorkflowTree.Node>> right) {
		if (left.size() > 0) {
			if (right.size() > 0) {
				double maxAlignment = 0;
				List<WorkflowTree.Node> sequence = left.remove(0);
				maxAlignment = calcAlignment(sequence, new ArrayList<WorkflowTree.Node>())
						+ sequentialAlignment(left, right);
				for (int i = 0; i < right.size(); i++) {
					List<WorkflowTree.Node> other = right.remove(i);
					double alignment = calcAlignment(sequence, other) + sequentialAlignment(left, right);
					if (alignment < maxAlignment) {
						maxAlignment = alignment;
					}
					right.add(i, other);
				}
				left.add(0, sequence);
				return maxAlignment;
			} else {
				double alignment = 0;
				for (List<WorkflowTree.Node> sequence : left) {
					alignment += calcAlignment(sequence, new ArrayList<WorkflowTree.Node>());
				}
				return alignment;
			}
		} else {
			if (right.size() > 0) {
				double alignment = 0;
				for (List<WorkflowTree.Node> sequence : right) {
					alignment += calcAlignment(new ArrayList<WorkflowTree.Node>(), sequence);
				}
				return alignment;
			} else {
				return 0;
			}
		}
	}

	private double calcAlignment(List<WorkflowTree.Node> left, List<WorkflowTree.Node> right) {
		double match = 0;
		double mismatch = 10;
		double indel = 1;

		double[][] grid = new double[left.size() + 1][right.size() + 1];

		grid[0][0] = 0;

		for (int i = 1; i <= left.size(); i++) {
			grid[i][0] = grid[i - 1][0] + indel;
		}
		for (int j = 1; j <= right.size(); j++) {
			grid[0][j] = grid[0][j - 1] + indel;
		}
		for (int i = 1; i <= left.size(); i++) {
			for (int j = 1; j <= right.size(); j++) {
				double potentialMatch = grid[i - 1][j - 1]
						+ (left.get(i - 1).equals(right.get(j - 1)) ? match : mismatch);
				double potentialDeletion = grid[i - 1][j] + indel;
				double potentialInsertion = grid[i][j - 1] + indel;
				grid[i][j] = Math.min(Math.min(potentialDeletion, potentialInsertion), potentialMatch);
			}
		}
		return grid[left.size()][right.size()];
	}
	
	private static double match = 0;
	private static double mismatch = 1;
	private static double indel = 1;

	private void needlemanWunsch(List<WorkflowTree.Node> left, List<WorkflowTree.Node> right, Node parent) {

		double[][] grid = new double[left.size() + 1][right.size() + 1];

		grid[0][0] = 0;

		for (int i = 1; i <= left.size(); i++) {
			grid[i][0] = grid[i - 1][0] + indel;
		}
		for (int j = 1; j <= right.size(); j++) {
			grid[0][j] = grid[0][j - 1] + indel;
		}
		for (int i = 1; i <= left.size(); i++) {
			for (int j = 1; j <= right.size(); j++) {
				double potentialMatch = grid[i - 1][j - 1]
						+ (left.get(i - 1).equals(right.get(j - 1)) ? match : mismatch);
				double potentialDeletion = grid[i - 1][j] + indel;
				double potentialInsertion = grid[i][j - 1] + indel;
				grid[i][j] = Math.min(Math.min(potentialDeletion, potentialInsertion), potentialMatch);
			}
		}

		// backtracing
		int i = left.size();
		int j = right.size();
		while (i > 0 || j > 0) {
			if (i > 0 && j > 0 && left.get(i - 1).equals(right.get(j - 1))
					&& grid[i][j] - match == grid[i - 1][j - 1]) {
				Node newNode = new Node(parent, null, new FlowNode(left.get(i - 1).getNodeContainer()),
						new FlowNode(right.get(j - 1).getNodeContainer()));
				newNode.fCode = CHANGE;
				i--;
				j--;
			} else if (i > 0 && j > 0 && grid[i][j] - mismatch == grid[i - 1][j - 1]) {
				Node newNode = new Node(parent, null, new FlowNode(left.get(i - 1).getNodeContainer()),
						new FlowNode(right.get(j - 1).getNodeContainer()));
				newNode.fCode = CHANGE;
				i--;
				j--;
			} else if (i > 0 && grid[i][j] - indel == grid[i - 1][j]) {
				Node newNode = new Node(parent, null, new FlowNode(left.get(i - 1).getNodeContainer()), null);
				newNode.fCode = CHANGE;
				i--;
			} else if (j > 0 && grid[i][j] - indel == grid[i][j - 1]) {
				Node newNode = new Node(parent, null, null, new FlowNode(right.get(j - 1).getNodeContainer()));
				newNode.fCode = CHANGE;
				j--;
			} else {
				System.out.println("error");
			}
		}
	}
}
