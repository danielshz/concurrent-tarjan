import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

class Tarjan {
	private int index = 0;
	private Stack<Node> controlStack;
	private Stack<Node> tarjanStack;
	private AdjacencyList adjList;
	private HashMap<Integer, Node> nodes;
	private ArrayList<Set<Integer>> SCCs;

	public Tarjan(AdjacencyList adjList, HashMap<Integer, Node> nodes) {
		this.index = 0;
		this.controlStack = new Stack<Node>();
		this.tarjanStack = new Stack<Node>();
		this.adjList = adjList;
		this.nodes = nodes;
		this.SCCs = new ArrayList<>();
	}

	private void addNode(Node node) {
		node.index = this.index;
		node.lowlink = this.index;
		node.status = Node.Status.INPROGRESS;

		this.index++;

		controlStack.push(node);
		tarjanStack.push(node);
	}

	public ArrayList<Set<Integer>> SequentialTarjan(Node startNode) {
		addNode(startNode);

		while(!controlStack.isEmpty()) {
			Node node = controlStack.lastElement();
			
			Set<Integer> outNeighbours = adjList.getOutEdges(node.id);
			
			if(outNeighbours != null && !outNeighbours.isEmpty()) {
				int childId = outNeighbours.iterator().next();
				adjList.removeOutEdge(node.id, childId);

				Node child = nodes.get(childId);

				if(child.status == Node.Status.UNSEEN)
					addNode(child);
				else if(tarjanStack.contains(child))
					node.updateLowLink(child.index);
			} else {
				controlStack.pop();

				if(!controlStack.isEmpty())
					controlStack.lastElement().updateLowLink(node.lowlink);
				
				if(node.lowlink == node.index) {
					Set<Integer> SCC = new HashSet<Integer>();

					Node vertex;

					do {
						vertex = tarjanStack.pop();

						SCC.add(vertex.id);
						vertex.status = Node.Status.COMPLETE;
					} while(vertex.id != node.id);

					SCCs.add(SCC);
				}
			}
		}

        return SCCs;
	}
}