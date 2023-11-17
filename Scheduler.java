import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;

// Classe que define um pool de threads
class Scheduler {
    private boolean shutdown;
    private final int nThreads;

    private final Search[] searches;
    private HashMap<Integer, Node> suspended;

	private Stack<Node>[] controlStacks;
	private Stack<Node>[] tarjanStacks;

	private AdjacencyList adjList;
	private HashMap<Integer, Node> nodes;
	private ArrayList<Set<Integer>> SCCs;

    private final LinkedList<Node> nodesToSearch;

    public Scheduler(int nThreads, AdjacencyList adjList, HashMap<Integer, Node> nodes) {
        this.shutdown = false;
        this.nThreads = nThreads;

        this.suspended = new HashMap<>();

        this.searches = new Search[nThreads];
        this.controlStacks = new Stack[nThreads];
        this.tarjanStacks = new Stack[nThreads];
        this.adjList = adjList;
        this.nodes = nodes;
        this.SCCs = new ArrayList<>();

        this.nodesToSearch = new LinkedList<>();

        for(int i = 0; i < nThreads; i++) {
            this.searches[i] = new Search(i);
            this.searches[i].start();
        } 
    }

    public synchronized void Suspend(int search, Node node) {
        suspended.put(search, node);

        HasBlokingCycle(search, node.search);
    }

    private synchronized boolean HasBlokingCycle(int search, int nodeSearch) {
        int currentSearch = nodeSearch;

        for(int i = 1; i < searches.length; i++) {
            Node node = suspended.get(currentSearch);

            if(node == null)
                return false;

            currentSearch = node.search;

            if(currentSearch == search)
                return true;
        }

        return false;
    }

    public void execute(Node node) {
        synchronized(nodesToSearch) {
            if(!this.shutdown) {
                this.nodesToSearch.addLast(node);
                this.nodesToSearch.notify();
            }
        }
    }
    
    public void shutdown() {
        synchronized(nodesToSearch) {
            this.shutdown = true;
            nodesToSearch.notifyAll();
        }

        for(int i = 0; i < searches.length; i++) {
            try {
                searches[i].join(); 
            } catch(InterruptedException e) {
                return; 
            }
        }
    }

    public synchronized Node getNewNode() {
        while(nodesToSearch.isEmpty() && (!shutdown)) {
            try { 
                nodesToSearch.wait(); 
            } catch(InterruptedException ignored) {}
        }

        if(nodesToSearch.isEmpty() && shutdown)
            return null;  

        return (Node) nodesToSearch.removeFirst();
    }

    private class Search extends Thread {
        public int waitingFor = -1;
        public int index = 0;
        public int searchId;

        public Search(int searchId) {
            this.searchId = searchId;
        }

        private void addNode(Node node) {
            node.index = this.index;
            node.lowlink = this.index;
            node.status = Node.Status.INPROGRESS;
            node.search = this.searchId;
    
            this.index++;
    
            controlStacks[searchId].push(node);
            tarjanStacks[searchId].push(node);
        }

        public void run() {
            Stack<Node> controlStack = controlStacks[searchId];
            Stack<Node> tarjanStack = tarjanStacks[searchId];

            Node startNode = getNewNode();

            if(startNode == null)
                return;

            addNode(startNode);

            while(!controlStack.isEmpty()) {
                Node node = controlStack.lastElement();

                synchronized(node) {
                    Set<Integer> outNeighbours = adjList.getOutEdges(node.id);
                    
                    if(outNeighbours != null && !outNeighbours.isEmpty()) {
                        int childId = outNeighbours.iterator().next();
                        adjList.removeOutEdge(node.id, childId);
    
                        Node child = nodes.get(childId);
    
                        synchronized(child) {
                            if(child.status == Node.Status.UNSEEN)
                                addNode(child);
                            else if(tarjanStack.contains(child))
                                node.updateLowLink(child.index);
                            else if(child.status != Node.Status.COMPLETE) {
                                try {
                                    child.wait();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
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
                                node.notifyAll();
                            } while(vertex.id != node.id);
    
                            SCCs.add(SCC);
                        }
                    }
                }
            }
        }
    }
}