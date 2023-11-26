import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Classe que define um pool de threads
class Scheduler {
    public boolean shutdown;

    public final Search[] searches;
    private Suspended suspended;
    private Integer nextNode = 0;

	private AdjacencyList adjList;
	private ConcurrentHashMap<Integer, Node> nodes;
	public ArrayList<Set<Integer>> SCCs;

    private final LinkedList<Node> nodesToSearch;

    public Scheduler(int nThreads, AdjacencyList adjList, ConcurrentHashMap<Integer, Node> nodes) {
        this.shutdown = false;
        this.suspended = new Suspended();

        this.searches = new Search[nThreads];
        this.adjList = adjList;
        this.nodes = nodes;
        this.SCCs = new ArrayList<>();

        this.nodesToSearch = new LinkedList<>();

        for(int i = 0; i < nThreads; i++) {
            this.searches[i] = new Search(i, this.suspended, this);
            this.searches[i].start();
        } 
    }

    public boolean getShutdown() {
        return this.shutdown;
    }

    public ArrayList<Set<Integer>> getSCCs() {
        return this.SCCs;
    }

    private void updateSCCs() {
        for(Search search : searches) {
			for(Set<Integer> SCC : search.getSCCs()) {
				this.SCCs.add(SCC);
			}
		}
    }

    public LinkedList<Node> getNodesToSearch() {
        return this.nodesToSearch;
    }

    public void execute() {
        Node startNode = Node.getNotInSCC(nodes, nextNode);

        if(startNode != null && startNode.status == Node.Status.UNSEEN) {
            queueNewNode(startNode);
        }
    }
    
    public void shutdown() {
        for(int i = 0; i < searches.length; i++) {
            try {
                searches[i].join(); 
            } catch(InterruptedException e) {
                return; 
            }
        }

        updateSCCs();
    }

    public Node getNewNode() {
        Node startNode;

        synchronized(nodesToSearch) {
            if(nodesToSearch.isEmpty()) {
                startNode = Node.getNotInSCC(nodes, nextNode);

                if(startNode == null) {
                    this.shutdown = true;
                    System.out.println("SHUTDOWN");
                }

                return startNode;
            }

            startNode = nodesToSearch.removeFirst();

            while(startNode.status != Node.Status.UNSEEN && !nodesToSearch.isEmpty())
                startNode = nodesToSearch.removeFirst();

            return startNode;
        }
    }

    public boolean queueNewNode(Node node) {
        synchronized(nodesToSearch) {
            if(nodesToSearch.size() < searches.length) {
                this.nodesToSearch.addLast(node);
                return true;
            }

            return false;
        }
    }
    
    public Node getUnexploredChild(Node node) {
        synchronized(adjList) {
            Set<Integer> outNeighbours = adjList.getOutEdges(node.id);
            
            if(outNeighbours != null && !outNeighbours.isEmpty()) {
                for(int childId : outNeighbours) {
                    Node child = nodes.get(childId);
                    return child;
                }
            }
    
            return null;
        }
    }

    public void removeEdge(Node node, Node child) {
        synchronized(adjList) {
            Set<Integer> outNeighbours = adjList.getOutEdges(node.id);
    
            if(outNeighbours != null && !outNeighbours.isEmpty()) {
                outNeighbours.remove(child.id);
            }
        }
    }

    public void queueChildren(Node node) {
        Set<Integer> outNeighbours = adjList.getOutEdges(node.id);

        synchronized(outNeighbours) {
            if(outNeighbours != null && !outNeighbours.isEmpty()) {
                for(int childId : outNeighbours) {
                    Node child = nodes.get(childId);

                    boolean added = false;

                    if(child.status == Node.Status.UNSEEN)
                        added = queueNewNode(child);

                    //if(added)
                        //System.out.println("= " + node.id + " -> " + child.id + " : s" + node.search.id);
                }
            }
        }
    }
}
