package workflowmatching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.knime.core.node.workflow.ConnectionContainer;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.NodeID;
import org.knime.core.node.workflow.WorkflowManager;

public class WorkflowTree {
	public class Node {

		private Node parent;
		private List<Node> children = new ArrayList<Node>();
		private NodeContainer nc;
		private WorkflowManager wfm;
		private Node[] inputs;
		private int[] inPorts;
		private Node[] metaOutputting;
		private int[] metaOutputtingPorts;
		private ArrayList<Set<Node>> metaInputReceivers;
		private ArrayList<HashMap<Node, Integer>> metaInputReceiverPorts;
		private ArrayList<Set<Node>> outputs;

		private ArrayList<HashMap<Node, Integer>> outPorts;

		public Node(WorkflowManager wfm) {
			this.setWorkflowManager(wfm);
			this.setNodeContainer(wfm);
			inputs = new Node[getNodeContainer().getNrInPorts()];
			inPorts = new int[getNodeContainer().getNrInPorts()];
			metaOutputting = new Node[getNodeContainer().getNrOutPorts()];
			metaOutputtingPorts = new int[getNodeContainer().getNrOutPorts()];
			outputs = new ArrayList<Set<Node>>();
			for (int i = 0; i < getNodeContainer().getNrOutPorts(); i++) {
				outputs.add(new HashSet<WorkflowTree.Node>());
			}
			outPorts = new ArrayList<HashMap<Node, Integer>>();
			for (int i = 0; i < getNodeContainer().getNrOutPorts(); i++) {
				outPorts.add(new HashMap<WorkflowTree.Node, Integer>());
			}
			metaInputReceivers = new ArrayList<Set<Node>>();
			for (int i = 0; i < getNodeContainer().getNrInPorts(); i++) {
				metaInputReceivers.add(new HashSet<WorkflowTree.Node>());
			}
			metaInputReceiverPorts = new ArrayList<HashMap<Node, Integer>>();
			for (int i = 0; i < getNodeContainer().getNrInPorts(); i++) {
				metaInputReceiverPorts.add(new HashMap<WorkflowTree.Node, Integer>());
			}
		}

		public Node(NodeContainer nc) {
			this.setNodeContainer(nc);
			inputs = new Node[nc.getNrInPorts()];
			inPorts = new int[inputs.length];
			outputs = new ArrayList<Set<Node>>();
			for (int i = 0; i < nc.getNrOutPorts(); i++) {
				outputs.add(new HashSet<WorkflowTree.Node>());
			}
			outPorts = new ArrayList<HashMap<Node, Integer>>();
			for (int i = 0; i < outputs.size(); i++) {
				outPorts.add(new HashMap<WorkflowTree.Node, Integer>());
			}
		}

		public void addChild(Node child) {
			children.add(child);
			child.setParent(this);
		}

		public void calcInOut() {
			if (getParent() != null) {
				Node root = this;
				while (root.getParent() != null) {
					root = root.getParent();
				}
				for (int i = 0; i < getNodeContainer().getNrInPorts(); i++) {
					ConnectionContainer cc = getParent().getWorkflowManager()
							.getIncomingConnectionFor(getNodeContainer().getID(), i);
					if (cc != null) {
						inputs[i] = cc != null ? root.getNode(cc.getSource()) : null;
						inPorts[i] = cc != null ? cc.getSourcePort() : -1;
						if (inputs[i] != null) {
						}
						if (inputs[i] == getParent()) {
							getParent().metaInputReceivers.get(cc.getSourcePort()).add(this);
							getParent().metaInputReceiverPorts.get(cc.getSourcePort()).put(this, i);
						}
					}
				}
				for (int i = 0; i < getNodeContainer().getNrOutPorts(); i++) {
					Set<ConnectionContainer> ccs = getParent().getWorkflowManager()
							.getOutgoingConnectionsFor(getNodeContainer().getID(), i);
					for (ConnectionContainer cc : ccs) {
						outputs.get(i).add(root.getNode(cc.getDest()));
						outPorts.get(i).put(root.getNode(cc.getDest()), cc.getDestPort());
						if (root.getNode(cc.getDest()) == getParent()) {
							getParent().metaOutputting[cc.getDestPort()] = this;
							getParent().metaOutputtingPorts[cc.getDestPort()] = i;
						}
					}
				}
			}
			for (Node n : children) {
				n.calcInOut();
			}
		}

		public List<Node> getNodes() {
			List<Node> output = new ArrayList<WorkflowTree.Node>();
			for (Node n : children) {
				output.addAll(n.getNodes());
			}
			return output;
		}

		public Node getNode(NodeContainer nc) {
			if (this.getNodeContainer().equals(nc)) {
				return this;
			}
			for (Node n : children) {
				Node child = n.getNode(nc);
				if (child != null) {
					return child;
				}
			}
			return null;
		}

		public Node getNode(NodeID id) {
			if (getNodeContainer().getID().equals(id)) {
				return this;
			}
			for (Node n : children) {
				Node child = n.getNode(id);
				if (child != null) {
					return child;
				}
			}
			return null;
		}

		/**
		 * 
		 * @param portIndex
		 * @return the Node that determines the input at {@code portIndex}
		 *         (ignores meta-nodes)
		 */
		public Node getInNode(int portIndex) {
			// Nothing here
			if (inputs[portIndex] == null) {
				return null;
			}
			// A node is what we want
			if (inputs[portIndex].isLeaf()) {
				return inputs[portIndex];
			}
			// If the input is outputting to this node, the input is a preceding
			// meta-node and we want to get the node in it that determines its
			// output
			if (inputs[portIndex].outputs.size() > inPorts[portIndex]
					&& inputs[portIndex].outputs.get(inPorts[portIndex]).contains(this)) {
				return inputs[portIndex].getOutputtingNode(inPorts[portIndex]);
			}
			// Otherwise this is the first node inside of a meta node. get the
			// input of the metanode
			return inputs[portIndex].getInNode(inPorts[portIndex]);
		}

		/**
		 * 
		 * @param portIndex
		 * @return the Nodes that receive the output of port {@code portIndex}
		 *         (ignores meta-nodes)
		 */
		public Collection<Node> getOutNodes(int portIndex) {
			Collection<Node> outNodes = new HashSet<WorkflowTree.Node>();
			// Nothing here
			if (outputs.get(portIndex).size() == 0) {
				return outNodes;
			}
			// get the nodes and further investigate the meta-nodes
			for (Node n : outputs.get(portIndex)) {
				// A node is what we want
				if (n.isLeaf()) {
					outNodes.add(n);
				} else {
					// it outputs to a meta-node
					int metaPort = outPorts.get(portIndex).get(n);
					// If the meta-node is the parent of this node, this node
					// determines the output of the meta-node
					if (this.isChildOf(n)) {
						outNodes.addAll(n.getOutNodes(metaPort));
					} else {
						// Otherwise it outputs INTO a meta-node
						outNodes.addAll(n.getReceivingNodes(metaPort));
					}
				}
			}
			return outNodes;
		}

		/**
		 * 
		 * @param portIndex
		 * @return the Nodes that receive the input of this meta-node at port
		 *         {@code portIndex} (ignores meta-nodes)
		 */
		public Collection<Node> getReceivingNodes(int portIndex) {
			Collection<Node> receivingNodes = new HashSet<WorkflowTree.Node>();
			for (Node n : metaInputReceivers.get(portIndex)) {
				if (n.isLeaf()) {
					receivingNodes.add(n);
				} else {
					receivingNodes.addAll(n.getReceivingNodes(metaInputReceiverPorts.get(portIndex).get(n)));
				}
			}
			return receivingNodes;
		}

		/**
		 * 
		 * @param portIndex
		 * @return the node that determines the output of this meta-node at
		 *         {@code portIndex} (ignores meta-nodes)
		 */
		public Node getOutputtingNode(int portIndex) {
			if (metaOutputting[portIndex].isLeaf()) {
				return metaOutputting[portIndex];
			}
			return metaOutputting[portIndex].getOutputtingNode(metaOutputtingPorts[portIndex]);
		}

		public boolean isAncestorOf(Node node) {
			if (children.contains(node)) {
				return true;
			}
			for (Node n : children) {
				if (n.isAncestorOf(node)) {
					return true;
				}
			}
			return false;
		}

		public boolean isChildOf(Node node) {
			Node cur = this;
			while (cur.getParent() != null && !cur.getParent().equals(node)) {
				cur = cur.getParent();
			}
			if (cur.getParent() == null) {
				return false;
			}
			return true;
		}

		public boolean isLeaf() {
			return false;
		}

		public boolean matches(Node node) {
			return matchProb(node) == 100;
		}

		public ArrayList<Node> getIncomings() {
			ArrayList<Node> incomings = new ArrayList<Node>();
			for (int i = 0; i < inputs.length; i++) {
				if (inputs[i] != null) {
					incomings.add(getInNode(i));
				}
			}

			return incomings;
		}

		public ArrayList<Node> getOutgoings() {
			ArrayList<Node> outgoings = new ArrayList<Node>();
			for (int i = 0; i < outputs.size(); i++) {
				if (outputs.get(i).size() > 0) {
					outgoings.addAll(getOutNodes(i));
				}
			}
			return outgoings;
		}

		public double matchProb(Node that) {
			ArrayList<Node> incomingsThis = this.getIncomings();
			ArrayList<Node> incomingsThat = that.getIncomings();
			ArrayList<Node> outgoingsThis = this.getOutgoings();
			ArrayList<Node> outgoingsThat = that.getOutgoings();
			ArrayList<Node> incomingsThis2 = new ArrayList<Node>();
			ArrayList<Node> incomingsThat2 = new ArrayList<Node>();
			ArrayList<Node> outgoingsThis2 = new ArrayList<Node>();
			ArrayList<Node> outgoingsThat2 = new ArrayList<Node>();
			ArrayList<Node> incomingsThis3 = new ArrayList<Node>();
			ArrayList<Node> incomingsThat3 = new ArrayList<Node>();
			ArrayList<Node> outgoingsThis3 = new ArrayList<Node>();
			ArrayList<Node> outgoingsThat3 = new ArrayList<Node>();

			for (Node n : incomingsThis) {
				incomingsThis2.addAll(n.getIncomings());
			}
			for (Node n : incomingsThat) {
				incomingsThat2.addAll(n.getIncomings());
			}
			for (Node n : outgoingsThis) {
				outgoingsThis2.addAll(n.getOutgoings());
			}
			for (Node n : outgoingsThat) {
				outgoingsThat2.addAll(n.getOutgoings());
			}

			incomingsThis.addAll(incomingsThis2);
			incomingsThat.addAll(incomingsThat2);
			outgoingsThis.addAll(outgoingsThis2);
			outgoingsThat.addAll(outgoingsThat2);

			for (Node n : incomingsThis2) {
				incomingsThis3.addAll(n.getIncomings());
			}
			for (Node n : incomingsThat2) {
				incomingsThat3.addAll(n.getIncomings());
			}
			for (Node n : outgoingsThis2) {
				outgoingsThis3.addAll(n.getOutgoings());
			}
			for (Node n : outgoingsThat2) {
				outgoingsThat3.addAll(n.getOutgoings());
			}

			incomingsThis.addAll(incomingsThis3);
			incomingsThat.addAll(incomingsThat3);
			outgoingsThis.addAll(outgoingsThis3);
			outgoingsThat.addAll(outgoingsThat3);

			int matches = 0;
			int mismatches = 0;
			Iterator<Node> inThisIt = incomingsThis.iterator();
			while (inThisIt.hasNext()) {
				Node inThis = inThisIt.next();
				Iterator<Node> inThatIt = incomingsThat.iterator();
				while (inThatIt.hasNext()) {
					Node inThat = inThatIt.next();
					if (inThis.getNodeContainer().getName().equals(inThat.getNodeContainer().getName())) {
						inThisIt.remove();
						inThatIt.remove();
						matches++;
						break;
					}
				}
			}
			Iterator<Node> outThisIt = outgoingsThis.iterator();
			while (outThisIt.hasNext()) {
				Node outThis = outThisIt.next();
				Iterator<Node> outThatIt = outgoingsThat.iterator();
				while (outThatIt.hasNext()) {
					Node outThat = outThatIt.next();
					if (outThis.getNodeContainer().getName().equals(outThat.getNodeContainer().getName())) {
						outThisIt.remove();
						outThatIt.remove();
						matches++;
						break;
					}
				}
			}

			mismatches = Math.max(incomingsThis.size(), incomingsThat.size())
					+ Math.max(outgoingsThis.size(), outgoingsThat.size());
			return matches * 100.0 / (matches + mismatches);
		}

		public String toString() {
			return getWorkflowManager().toString();
		}

		public NodeContainer getNodeContainer() {
			return nc;
		}

		public void setNodeContainer(NodeContainer nc) {
			this.nc = nc;
		}

		public WorkflowManager getWorkflowManager() {
			return wfm;
		}

		public void setWorkflowManager(WorkflowManager wfm) {
			this.wfm = wfm;
		}

		public Node getParent() {
			return parent;
		}

		public void setParent(Node parent) {
			this.parent = parent;
		}

		public int getNumOfInputs() {
			int numOfInputs = 0;
			for (int i = 0; i < inputs.length; i++) {
				if (getInNode(i) != null) {
					numOfInputs++;
				}
			}
			return numOfInputs;
		}

		public int getNumOfOutputs() {
			int numOfOutputs = 0;
			for (int i = 0; i < outputs.size(); i++) {
				numOfOutputs += getOutNodes(i).size();
			}
			return numOfOutputs;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Node) {
				Node that = (Node) obj;
				int thisIndex = this.nc.getID().getIndex();
				int thatIndex = that.nc.getID().getIndex();
				return this.nc.getName().equals(that.nc.getName()) && thisIndex == thatIndex;
			}
			return super.equals(obj);
		}
	}

	public class Leaf extends Node {

		public Leaf(NodeContainer nc) {
			super(nc);
		}

		public boolean isLeaf() {
			return true;
		}

		public String toString() {
			return getNodeContainer().toString();
		}

		public List<Node> getNodes() {
			List<Node> output = new ArrayList<WorkflowTree.Node>();
			output.add(this);
			return output;
		}
	}

	Node root;

	public WorkflowTree(WorkflowManager wfm) {
		root = new Node(wfm);
		Collection<NodeContainer> ncs = wfm.getNodeContainers();
		for (NodeContainer nc : ncs) {
			if (nc instanceof WorkflowManager) {
				WorkflowTree metaTree = new WorkflowTree((WorkflowManager) nc);
				Node metaRoot = metaTree.root;
				root.addChild(metaRoot);
			} else {
				Node n = new Leaf(nc);
				root.addChild(n);
			}
		}
		root.calcInOut();
	}

	public List<Node> getNodes() {
		List<Node> output = new ArrayList<WorkflowTree.Node>();
		output.addAll(root.getNodes());
		return output;
	}

	public Node getNode(NodeContainer nc) {
		return root.getNode(nc);
	}

	public Node getNode(NodeID id) {
		return root.getNode(id);
	}

	public List<List<Node>> toSequences() {
		List<List<Node>> sequences = new ArrayList<List<Node>>();
		List<Node> endNodes = new ArrayList<Node>();
		HashMap<Node, Boolean> done = new HashMap<WorkflowTree.Node, Boolean>();
		for (Node n : getNodes()) {
			done.put(n, false);
			if (n.getNumOfOutputs() == 0) {
				endNodes.add(n);
			}
		}

		while (endNodes.size() > 0) {
			int[] sizes = new int[endNodes.size()];
			for (int i = 0; i < endNodes.size(); i++) {
				Node endNode = endNodes.get(i);
				List<Node> sequence = getSequenceFrom(endNode, done);
				sizes[i] = sequence.size();
				for (int j = 0; j < sequence.size(); j++) {
					done.put(sequence.get(j), false);
				}
			}

			int max = 0;
			for (int i = 1; i < sizes.length; i++) {
				if (sizes[i] > sizes[max]) {
					max = i;
				}
			}
			sequences.add(getSequenceFrom(endNodes.get(max), done));
			endNodes.remove(max);
		}
		return sequences;
	}

	public List<Node> getSequenceFrom(Node endNode, HashMap<Node, Boolean> done) {
		ArrayList<Node> sequence = new ArrayList<Node>();
		Stack<Node> stack = new Stack<WorkflowTree.Node>();
		stack.push(endNode);
		while (!stack.isEmpty()) {
			Node top = stack.peek();
			for (Node before : top.getIncomings()) {
				if (!done.get(before)) {
					stack.push(before);
					break;
				}
			}
			if (top.equals(stack.peek())) {
				sequence.add(stack.pop());
				done.put(top, true);
			}
		}
		return sequence;
	}
}
