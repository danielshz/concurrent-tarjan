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
            this.searches[i] = new Search(i, this.suspended, this.adjList, this, this.nodes);
            this.searches[i].start();
        } 
    }

    public boolean getShutDown() {
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

                if(startNode == null)
                    this.shutdown = true;

                return startNode;
            }

            startNode = nodesToSearch.removeFirst();

            while(startNode.status != Node.Status.UNSEEN && !nodesToSearch.isEmpty())
                startNode = nodesToSearch.removeFirst();

            return startNode;
        }
    }

    public void queueNewNode(Node node) {
        synchronized(nodesToSearch) {
            this.nodesToSearch.addLast(node);
            this.nodesToSearch.notify();
        }
    }	
}
