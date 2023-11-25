import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Node {
	public enum Status {
		COMPLETE,
		INPROGRESS,
		UNSEEN,
	}

	public int id;
	public int index;
	public int lowlink;
	public Status status;
	public Search search;
	public ArrayList<Search> blocked;

	public Node(int id, int index, int lowlink) {
		this.id = id;
		this.index = index;
		this.lowlink = lowlink;
		this.status = Status.UNSEEN;
		this.blocked = new ArrayList<>();
		this.search = null;
	}

	public synchronized void updateLowLink(int update) {
		this.lowlink = Math.min(this.lowlink, update);
	}

    public static HashMap<Integer, Node> getNodeMap(Set<Integer> nodes) {
        HashMap<Integer, Node> nodeMap = new HashMap<>();

        for(int nodeId : nodes)
            nodeMap.put(nodeId, new Node(nodeId, -1, -1));
        
        return nodeMap;
    }

	public synchronized static Node getNotInSCC(Map<Integer, Node> nodes, Integer nextNode) {
		if(nextNode >= nodes.size())
			return null;

		Node node = nodes.get(nextNode);
		
		while(nextNode < nodes.size()) {
			if(node.status == Status.UNSEEN)
				return node;
			
			nextNode++;
			node = nodes.get(nextNode);
		}

		return null;
	}
}