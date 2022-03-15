/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   12.11.2015 (ferry.abt@knime.com): created
 */
package org.knime.workbench.workflowdiff.workflowcompare;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.knime.core.node.workflow.ConnectionContainer;
import org.knime.core.node.workflow.NodeContainer;
import org.knime.core.node.workflow.NodeID;
import org.knime.core.node.workflow.WorkflowManager;

/**
 * A class to store a workflow as a tree with methods to facilitate matching
 * 
 * @author Ferry Abt (ferry.abt@knime.com)
 *
 */
public class WorkflowTree {

	/**
	 * 
	 * A Node for the tree. Workflow, Meta-Node or Sub-Node
	 *
	 */
	public class Node {

		/**
		 * parent Node, only {@code null} iff root
		 */
		private Node parent;
		/**
		 * children of this node
		 */
		private List<Node> children = new ArrayList<Node>();
		/**
		 * NodeContainer of the KNIME-Node which is represented by this node.
		 * Must not be null.
		 */
		private final NodeContainer nc;
		/**
		 * If representing a Workflow/Meta-/Sub-Node the WorkflowManager is
		 * stored here. Only null in Leaves.
		 */
		private final WorkflowManager wfm;
		/**
		 * 1:1 representation of the Nodes' inputs. May have {@code length} 0!
		 */
		private Node[] inputs;
		/**
		 * The ports of the Nodes, which are connected to this Node.
		 * {@code inPorts[0]=0} means the first output port of Node
		 * {@code input[0]} is connected to the first input port of {@code this}
		 * Node
		 */
		private int[] inPorts;
		/**
		 * If this is a Meta-Node, the Nodes, which determine the output of this
		 * Node are stored here. DO NOT ACCESS IN LEAVES!
		 */
		private Node[] metaOutputting;
		/**
		 * Same as {@link #inPorts} but for the children of this Meta-Node, that
		 * are connected to the output of this Meta-Node. DO NOT ACCESS IN
		 * LEAVES!
		 */
		private int[] metaOutputtingPorts;
		/**
		 * The children of this Meta-Node that receive the input. One set for
		 * each port. A set might be empty but never null. DO NOT ACCESS IN
		 * LEAVES!
		 */
		private ArrayList<Set<Node>> metaInputReceivers;
		/**
		 * A list of HashMaps to get the inPorts of the receiving Nodes. One
		 * HashMap per port. See {@link #inPorts}. DO NOT ACCESS IN LEAVES!
		 */
		private ArrayList<HashMap<Node, Integer>> metaInputReceiverPorts;
		/**
		 * The nodes that receive their input from this Node. One set per
		 * outPort.
		 */
		private ArrayList<Set<Node>> outputs;
		/**
		 * See {@link #metaInputReceiverPorts}
		 */
		private ArrayList<HashMap<Node, Integer>> outPorts;

		/**
		 * Constructor for a Meta-Node, Sub-Node or Workflow in the WorkflowTree
		 * 
		 * @param wfm
		 *            the WorkflowManager of the Node/Workflow. Must not be
		 *            null!
		 */
		public Node(WorkflowManager wfm) {
			if (wfm == null) {
				throw new InvalidParameterException("WorkflowManager must not be null");
			}

			// Create link to the KNIME-Node
			this.wfm = wfm;
			this.nc = wfm;

			// Prepare structures to store connections
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

		/**
		 * Internal constructor for subclass {@link Leaf}
		 * 
		 * @param nc
		 *            The NodeContainer of the Leaf. Must not be null!
		 */
		private Node(NodeContainer nc) {
			if (nc == null) {
				throw new InvalidParameterException("NodeContainer must not be null");
			}

			// Create link to the KNIME-Node
			this.nc = nc;
			this.wfm = null;

			// Prepare structures to store connections
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

		/**
		 * Adds a child to this node and sets {@code this} as the parent of
		 * {@code child}
		 * 
		 * @param child
		 */
		public void addChild(Node child) {
			children.add(child);
			child.setParent(this);
		}

		/**
		 * Calculates the in and outputs of this node. Only call after all Nodes
		 * have been inserted.
		 */
		public void calcInOut() {
			// The root has no connections, therefore only look at the children.
			if (getParent() != null) {
				// Find the real root by walking up the tree
				Node root = this;
				while (root.getParent() != null) {
					root = root.getParent();
				}

				// Iterate over the inPorts
				for (int i = 0; i < getNodeContainer().getNrInPorts(); i++) {
					ConnectionContainer cc = getParent().getWorkflowManager()
							.getIncomingConnectionFor(getNodeContainer().getID(), i);
					// If there is Node connected
					if (cc != null && root.getNode(cc.getSource()) != root) {
						// Store the connection
						inputs[i] = root.getNode(cc.getSource());
						inPorts[i] = cc.getSourcePort();
						// If the connection is to the parent of this node, then
						// this node receives the input of a Meta-Node. Store
						// that information in the parent.
						if (inputs[i] == getParent()) {
							getParent().metaInputReceivers.get(cc.getSourcePort()).add(this);
							getParent().metaInputReceiverPorts.get(cc.getSourcePort()).put(this, i);
						}
					}
				}
				// Iterate over the outPorts
				for (int i = 0; i < getNodeContainer().getNrOutPorts(); i++) {
					Set<ConnectionContainer> ccs = getParent().getWorkflowManager()
							.getOutgoingConnectionsFor(getNodeContainer().getID(), i);
					// Look at all connections to an outPort
					for (ConnectionContainer cc : ccs) {
						// Store the connections
						outputs.get(i).add(root.getNode(cc.getDest()));
						outPorts.get(i).put(root.getNode(cc.getDest()), cc.getDestPort());
						// If the output is received by the parent of this node,
						// then this node determines the output of a Meta-Node.
						// Store this information in the parent.
						if (root.getNode(cc.getDest()) == getParent()) {
							getParent().metaOutputting[cc.getDestPort()] = this;
							getParent().metaOutputtingPorts[cc.getDestPort()] = i;
						}
					}
				}
			}
			// Continue with the children
			for (Node n : children) {
				n.calcInOut();
			}
		}

		/**
		 * 
		 * @return all Leaves, meaning KNIME-Nodes, in this tree. No Meta- or
		 *         Sub-Nodes or Workflows.
		 */
		public List<Node> getNodes() {
			List<Node> output = new ArrayList<WorkflowTree.Node>();
			for (Node n : children) {
				output.addAll(n.getNodes());
			}
			return output;
		}

		/**
		 * Finds and returns the node in this tree that represents the
		 * KNIME-Node {@code nc}
		 * 
		 * @param nc
		 *            the KNIME-Node to be found
		 * @return the node in this tree that represents the KNIME-Node
		 *         {@code nc} or {@code null} if not present in this tree
		 */
		public Node getNode(NodeContainer nc) {
			return getNode(nc.getID());
		}

		/**
		 * Finds and returns the node in this tree that represents the
		 * KNIME-Node with the ID{@code id}
		 * 
		 * @param id
		 *            the ID of the KNIME-Node to be found
		 * @return the node in this tree that represents the KNIME-Node with the
		 *         ID {@code id} or {@code null} if not present in this tree
		 */
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
		 *         (ignores Sub- and Meta-Nodes)
		 */
		public Node getInNode(int portIndex) {
			if (this == root) {
				return null;
			}
			// Nothing here
			if (inputs[portIndex] == null) {
				return null;
			}
			// A node is what we want
			if (inputs[portIndex].isLeaf()) {
				return inputs[portIndex];
			}
			// At this point the input has to be a Meta-Node. If it's the parent
			// of this node, this node is the first node in it and receives the
			// input from outside. Determine the input of the parent.
			if (inputs[portIndex].equals(this.getParent())) {
				if (this.getParent() != root) {
					return inputs[portIndex].getInNode(inPorts[portIndex]);
				} else {
					return null;
				}
			}
			// Now this node has to receive its input from the output of a
			// Meta-Node. Determine the outputting node of the Meta-Node.
			return inputs[portIndex].getOutputtingNode(inPorts[portIndex]);
		}

		/**
		 * 
		 * @param portIndex
		 * @return a collection of the nodes that are receiving the output of
		 *         port {@code portIndex} (ignores meta-nodes)
		 */
		public Collection<Node> getOutNodes(int portIndex) {
			Collection<Node> outNodes = new HashSet<WorkflowTree.Node>();
			if (this == root) {
				return outNodes;
			}
			// get the nodes and further investigate the Meta-Nodes
			for (Node n : outputs.get(portIndex)) {
				// A node is what we want
				if (n.isLeaf()) {
					outNodes.add(n);
				} else {
					// it outputs to a meta-node
					int metaPort = outPorts.get(portIndex).get(n);
					// If the meta-node is the parent of this node, this node
					// determines the output of the Meta-Node, therefore get the
					// nodes connected to the outPort of the parent
					if (this.isDescendantOf(n)) {
						outNodes.addAll(n.getOutNodes(metaPort));
					} else {
						// Otherwise it outputs INTO a meta-node, therefore get
						// the nodes that receive the input of the Meta-Node
						outNodes.addAll(n.getReceivingNodes(metaPort));
					}
				}
			}
			return outNodes;
		}

		/**
		 * 
		 * @param portIndex
		 * @return the Nodes that receive the input of this Meta-Node at port
		 *         {@code portIndex} (ignores meta-nodes)
		 */
		public Collection<Node> getReceivingNodes(int portIndex) {
			Collection<Node> receivingNodes = new HashSet<WorkflowTree.Node>();
			// Iterate over all receiving nodes
			for (Node n : metaInputReceivers.get(portIndex)) {
				// A leaf is what we want
				if (n.isLeaf()) {
					receivingNodes.add(n);
				} else {
					// This is the parent of n, therefore we input into a
					// Meta-Node or Sub-Node
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
		    if (metaOutputting[portIndex] == null) {
		        return null;
		    }
			// A Leaf is what we want.
			if (metaOutputting[portIndex].isLeaf()) {
				return metaOutputting[portIndex];
			}
			// We have to go deeper
			return metaOutputting[portIndex].getOutputtingNode(metaOutputtingPorts[portIndex]);
		}

		/**
		 * Checks whether this is an ancestor of {@code node} (No matter how
		 * many nodes are in between)
		 * 
		 * @param node
		 * @return {@code true} if {@code node} is a descendant
		 */
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

		/**
		 * Checks whether this is a descendant of {@code node}
		 * 
		 * @param node
		 * @return {@code true} if {@code node} is an ancestor
		 */
		public boolean isDescendantOf(Node node) {
			Node cur = this;
			while (cur.getParent() != null && !cur.getParent().equals(node)) {
				cur = cur.getParent();
			}
			if (cur.getParent() == null) {
				return false;
			}
			return true;
		}

		/**
		 * 
		 * @return true if this is a leaf and therefore represents a KNIME-Node
		 *         and no Meta-Node, Sub-Node or Workflow
		 */
		public boolean isLeaf() {
			return false;
		}

		/**
		 * 
		 * @return all leaves that determine the input of this node.
		 */
		public ArrayList<Node> getIncomings() {
			// Calls getInNode on all used inPorts
			ArrayList<Node> incomings = new ArrayList<Node>();
			for (int i = 0; i < inputs.length; i++) {
				if (inputs[i] != null) {
					Node in = getInNode(i);
					if (in != null) {
						incomings.add(in);
					}
				}
			}
			return incomings;
		}

		/**
		 * 
		 * @return all leaves that receive the output of this node
		 */
		public ArrayList<Node> getOutgoings() {
			// Calls getOutNode on all used outPorts
			ArrayList<Node> outgoings = new ArrayList<Node>();
			for (int i = 0; i < outputs.size(); i++) {
				if (outputs.get(i).size() > 0) {
					outgoings.addAll(getOutNodes(i));
				}
			}
			return outgoings;
		}

		@Override
		public String toString() {
			return getWorkflowManager().toString();
		}

		/**
		 * 
		 * @return the NodeContainer this node represents
		 */
		public NodeContainer getNodeContainer() {
			return nc;
		}

		/**
		 * 
		 * @return the WorkflowManager this node represents. {@code null} iff
		 *         this is a leaf
		 */
		public WorkflowManager getWorkflowManager() {
			return wfm;
		}

		/**
		 * 
		 * @return the parent of this node. {@code null} iff this is a leaf
		 */
		public Node getParent() {
			return parent;
		}

		/**
		 * Sets the parent of this node to {@code parent}
		 * 
		 * @param parent
		 */
		public void setParent(Node parent) {
			this.parent = parent;
		}

		/**
		 * 
		 * @return the number of used inputs
		 */
		public int getNumOfInputs() {
			int numOfInputs = 0;
			for (int i = 0; i < inputs.length; i++) {
				if (getInNode(i) != null) {
					numOfInputs++;
				}
			}
			return numOfInputs;
		}

		/**
		 * 
		 * @return the number of leaves that receive their input from this node.
		 */
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
			return false;
		}

		@Override
		public int hashCode() {
			return (this.nc.getName() + this.nc.getID().getIndex()).hashCode();
		}

		public double getMatchQuality(Node that) {
			final double TYPEWEIGHT = 1;
			final double IDWEIGHT = 1;
			final double NUMOFINPUTSWEIGHT = 1;
			final double NUMOFOUTPUTSWEIGHT = 1;
			final double INPUTSUPERCLIQUEWEIGHT = 1;
			final double TYPEIDWEIGHT = 5;
			ArrayList<Double> qualities = new ArrayList<Double>();
			ArrayList<Double> weights = new ArrayList<Double>();

			// TYPE
			qualities.add(this.getTypeMatchQuality(that));
			weights.add(TYPEWEIGHT);

			// ID
			qualities.add(this.getIDMatchQuality(that));
			weights.add(IDWEIGHT);

			// TYPE+ID
			qualities.add(qualities.get(0) * qualities.get(1));
			weights.add(TYPEIDWEIGHT);

			// NUM OF INPUTS
			qualities.add(this.getNumOfInputsMatchQuality(that));
			weights.add(NUMOFINPUTSWEIGHT);

			// NUM OF OUTPUTS
			qualities.add(this.getNumOfOutputsMatchQuality(that));
			weights.add(NUMOFOUTPUTSWEIGHT);

			// INPUT SUPERCLIQUE
			qualities.add(this.getInputSupercliqueMatchQuality(that));
			weights.add(INPUTSUPERCLIQUEWEIGHT);

			double quality = 0;
			double totalWeight = 0;
			for (int i = 0; i < qualities.size(); i++) {
				quality += qualities.get(i) * weights.get(i);
				totalWeight += weights.get(i);
			}

			return quality / totalWeight;
		}

		private double getTypeMatchQuality(Node that) {
			if (this.getNodeContainer().getType().equals(that.getNodeContainer().getType())) {
				return 1;
			}
			return 0;
		}

		private double getIDMatchQuality(Node that) {
			if (this.nc.getID().getIndex() == that.nc.getID().getIndex()) {
				return 1;
			}
			return 0;
		}

		private double getNumOfInputsMatchQuality(Node that) {
			int thisNum = this.getNumOfInputs();
			int thatNum = that.getNumOfInputs();
			if (thisNum == 0 && thatNum == 0) {
				return 1;
			}
			return Math.min(thisNum, thatNum) / Math.max(thisNum, thatNum);
		}

		private double getNumOfOutputsMatchQuality(Node that) {
			int thisNum = this.getNumOfOutputs();
			int thatNum = that.getNumOfOutputs();
			if (thisNum == 0 && thatNum == 0) {
				return 1;
			}
			return Math.min(thisNum, thatNum) / Math.max(thisNum, thatNum);
		}

		private double getInputSupercliqueMatchQuality(Node that) {
			int thisNum = this.inputs.length;
			int thatNum = that.inputs.length;
			if (thisNum == 0 && thatNum == 0) {
				return 1;
			}
			int matches = 0;
			for (int i = 0; i < Math.min(thisNum, thatNum); i++) {
				if (this.inputs[i] != null && that.inputs[i] != null && this.inputs[i].equals(that.inputs[i])) {
					matches++;
				}
				if (this.inputs[i] == null && that.inputs[i] == null) {
					matches++;
				}
			}
			return matches * 1.0 / Math.max(thisNum, thatNum);
		}
	}

	/**
	 * 
	 * A Leaf for the tree. Represents a KNIME-Node.
	 *
	 */
	public class Leaf extends Node {

		/**
		 * Constructor for a KNIME-Node {@code nc} in the tree
		 * 
		 * @param nc
		 *            the KNIME-Node to be represented
		 */
		public Leaf(NodeContainer nc) {
			super(nc);
		}

		@Override
		public boolean isLeaf() {
			return true;
		}

		@Override
		public String toString() {
			return getNodeContainer().toString();
		}

		@Override
		public List<Node> getNodes() {
			// We are in a leaf, the only node in this subtree is this.
			List<Node> output = new ArrayList<WorkflowTree.Node>();
			output.add(this);
			return output;
		}
	}

	/**
	 * The root of this tree. Not always up-to-date TODO keep updated
	 */
	private Node root;

	/**
	 * Constructs recursively a WorkflowTree with {@code wfm} as root.
	 * 
	 * @param wfm
	 *            The workflow to be represented by this tree
	 */
	public WorkflowTree(WorkflowManager wfm) {
		// Create the root
		root = new Node(wfm);
		// Recursively work through the kids. Append the Nodes as leaves and
		// create sub-trees from the Containers
		for (NodeContainer nc : wfm.getNodeContainers()) {
			if (nc instanceof WorkflowManager) {
				WorkflowManager metaWFM = (WorkflowManager) nc;
				if (metaWFM.isEncrypted()) {
					Node n = new Leaf(nc);
					root.addChild(n);
				} else {
					WorkflowTree metaTree = new WorkflowTree(metaWFM);
					Node metaRoot = metaTree.root;
					root.addChild(metaRoot);
					metaTree.root=root;
				}
			} else {
				Node n = new Leaf(nc);
				root.addChild(n);
			}
		}
		// As the skeleton is ready calculate the connections
		root.calcInOut();
	}

	/**
	 * 
	 * @return all leaves, speak KNIME-Nodes, in this tree
	 */
	public List<Node> getNodes() {
		return root == null ? new ArrayList<WorkflowTree.Node>() : root.getNodes();
	}

	/**
	 * 
	 * @param nc
	 * @return the node in this tree that represents the KNIME-Node {@code nc}
	 *         or {@code null} if not present.
	 */
	public Node getNode(NodeContainer nc) {
		return root.getNode(nc);
	}

	/**
	 * 
	 * @param id
	 * @return the node in this tree that represents the KNIME-Node with the ID
	 *         {@code id} or {@code null} if not present.
	 */
	public Node getNode(NodeID id) {
		return root.getNode(id);
	}

	/**
	 * 
	 * @return this tree as a list of sequences
	 */
	public List<List<Node>> toSequences() {
		// the list of sequences to be returned
		List<List<Node>> sequences = new ArrayList<List<Node>>();
		// the list of nodes, that have no nodes connected to their outPorts
		List<Node> endNodes = new ArrayList<Node>();
		// Keep track of the nodes that are already sequenced
		HashMap<Node, Boolean> done = new HashMap<WorkflowTree.Node, Boolean>();

		// Find the endNodes
		for (Node n : getNodes()) {
			done.put(n, false);
			if (n.getNumOfOutputs() == 0) {
				endNodes.add(n);
			}
		}

		// Extract the longest possible sequence until no unsequenced nodes are
		// left.
		while (endNodes.size() > 0) {
			// Store the length of the sequence for endNode i in sizes[i]
			int[] sizes = new int[endNodes.size()];

			// Calculate the length of the sequence for each endNode
			for (int i = 0; i < endNodes.size(); i++) {
				Node endNode = endNodes.get(i);
				List<Node> sequence = getSequenceFrom(endNode, done);
				sizes[i] = sequence.size();
				// Revert the "sequenced" marks
				for (int j = 0; j < sequence.size(); j++) {
					done.put(sequence.get(j), false);
				}
			}

			// Identify the endNode with the longest sequence
			int max = 0;
			for (int i = 1; i < sizes.length; i++) {
				if (sizes[i] > sizes[max]) {
					max = i;
				}
			}

			// Calculate the sequence and remove the endNode.
			sequences.add(getSequenceFrom(endNodes.get(max), done));
			endNodes.remove(max);
		}
		return sequences;
	}

	/**
	 * Calculates the sequence for endNode {@code endNode} (The nodes that have
	 * to be executed to execute it, minus the nodes that have already been
	 * sequenced)
	 * 
	 * @param endNode
	 *            the node to calculate the sequence for
	 * @param done
	 *            the already sequenced nodes
	 * @return the sequence for Node {@code endNode}
	 */
	public List<Node> getSequenceFrom(Node endNode, HashMap<Node, Boolean> done) {
		// The sequence to be returned
		ArrayList<Node> sequence = new ArrayList<Node>();
		// Depth-First-Search, therefore a stack.
		Stack<Node> stack = new Stack<WorkflowTree.Node>();

		// Start at the endNode
		stack.push(endNode);
		while (!stack.isEmpty()) {
			// Check if the node on the top of the stack is already executable
			// (all inputs are done)
			Node top = stack.peek();
			for (Node before : top.getIncomings()) {
				if (!done.get(before)) {
					// If not, push the first unfinished node.
					stack.push(before);
					break;
				}
			}
			// If nothing has been pushed the top is executable and is therefore
			// added to the sequence, popped of the stack and marked as done.
			if (top.equals(stack.peek())) {
				sequence.add(stack.pop());
				done.put(top, true);
			}
		}
		return sequence;
	}
}
