import java.util.ArrayList;
import java.util.HashMap;
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
	public int search;
	public ArrayList<Integer> blocked;

	public Node(int id, int index, int lowlink) {
		this.id = id;
		this.index = index;
		this.lowlink = lowlink;
		this.status = Status.UNSEEN;
		this.blocked = new ArrayList<>();
		this.search = -1;
	}

	public void updateLowLink(int update) {
		this.lowlink = Math.min(this.lowlink, update);
	}

    public static HashMap<Integer, Node> getNodeMap(Set<Integer> nodes) {
        HashMap<Integer, Node> nodeMap = new HashMap<>();

        for(int nodeId : nodes)
            nodeMap.put(nodeId, new Node(nodeId, -1, -1));
        
        return nodeMap;
    }

	public static Node getNotInSCC(HashMap<Integer, Node> nodes) {
		for (Node node : nodes.values()) {
			if(node.status != Status.COMPLETE)
				return node;
		}

		return null;
	}
}