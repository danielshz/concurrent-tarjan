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
	public boolean exploredAllEdges;

	public Node(int id, int index, int lowlink) {
		this.id = id;
		this.index = index;
		this.lowlink = lowlink;
		this.status = Status.UNSEEN;
		this.exploredAllEdges = false;
	}

	public void updateLowLink(int update) {
		this.lowlink = Math.min(this.lowlink, update);
	}

    public static HashMap<Integer, Node> getNodeMap(Set<Integer> nodes) {
        HashMap<Integer, Node> nodeMap = new HashMap<>();

        for (int nodeId : nodes)
            nodeMap.put(nodeId, new Node(nodeId, -1, -1));
        
        return nodeMap;
    }
}