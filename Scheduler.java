import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

// Classe que define um pool de threads
class Scheduler {
    private boolean shutdown;
    private final int nThreads;

    private final Search[] searches;
    private HashMap<Integer, Node> suspended;

	private ArrayList<Stack<Node>> controlStacks;
	private ArrayList<Stack<Node>> tarjanStacks;

	private AdjacencyList adjList;
	private ConcurrentHashMap<Integer, Node> nodes;
	private ArrayList<Set<Integer>> SCCs;

    private final LinkedList<Node> nodesToSearch;

    public Scheduler(int nThreads, AdjacencyList adjList, ConcurrentHashMap<Integer, Node> nodes) {
        this.shutdown = false;
        this.nThreads = nThreads;

        this.suspended = new HashMap<>();

        this.searches = new Search[nThreads];
        this.controlStacks = new ArrayList<>();
        this.tarjanStacks = new ArrayList<>();
        this.adjList = adjList;
        this.nodes = nodes;
        this.SCCs = new ArrayList<>();

        this.nodesToSearch = new LinkedList<>();

        for(int i = 0; i < nThreads; i++) {
            this.controlStacks.add(new Stack<>());
            this.tarjanStacks.add(new Stack<>());
            this.searches[i] = new Search(i);
            this.searches[i].start();
        } 
    }

    public boolean getShutDown() {
        return this.shutdown;
    }
    public ArrayList<Set<Integer>> getSCCs() {
        return this.SCCs;
    }

    public LinkedList<Node> getNodesToSearch() {
        return this.nodesToSearch;
    }

    public synchronized boolean Suspend(Search search, Node node, Node child) {
        ArrayList<Integer> blokingCycle = BlokingCycle(search.searchId, child.search);

        if(blokingCycle == null) {
            node.blocked.add(search.searchId);
            search.waitingFor = child.id;
            suspended.put(search.searchId, node);

            return true;
        }
        
        TransferNodes(search, blokingCycle);
        child.notifyAll();
          
        return false;
    }

    public synchronized void Unsuspend(Search search, Node node) {
        suspended.remove(search.searchId);
        search.waitingFor = -1;
        node.blocked.remove(search.searchId);
    }

    private synchronized ArrayList<Integer> BlokingCycle(int search, int childSearch) {
        int currentSearch = childSearch;
        
        ArrayList<Integer> searchesInCycle = new ArrayList<>();
        searchesInCycle.add(currentSearch);

        for(int i = 1; i < searches.length; i++) {
            Node node = suspended.get(currentSearch);

            if(node == null)
                return null;

            currentSearch = node.search;
            
            if(currentSearch == search)
                return searchesInCycle;

            searchesInCycle.add(currentSearch);
        }

        return null;
    }

    private synchronized void TransferNodes(Search receiverSearch, ArrayList<Integer> blokingCycle) {
        Stack<Node> tarjanStack = tarjanStacks.get(receiverSearch.searchId);

        for(int senderId : blokingCycle) {
            Stack<Node> senderTarjanStack = tarjanStacks.get(senderId);
            Stack<Node> tempStack = new Stack<>();

            Node node;

            do {
                node = senderTarjanStack.pop();
                tempStack.push(node);
            } while(node.id != node.lowlink);

            // Atualizando lista de buscas bloqueadas do nó
            int senderWaitingNode = this.searches[senderId].waitingFor;
            this.nodes.get(senderWaitingNode).blocked.remove(senderId);
            
            // Atualizando a busca para o novo nó esperado
            this.searches[senderId].waitingFor = node.id;
            
            // Adicionando a busca bloqueada
            node.blocked.add(senderId);
            
            // Atualizando o suspended
            suspended.put(senderId, node);

            int baseIndex = receiverSearch.index - node.index;

            while(!tempStack.isEmpty()) {
                node = tempStack.pop();
                node.index += baseIndex;
                node.lowlink += baseIndex;
                node.search = receiverSearch.searchId;

                tarjanStack.push(node);
            }

            receiverSearch.index = node.index + 1;
        }
    }

    public void execute(Node node) {
        synchronized(nodesToSearch) {
            if(!this.shutdown && node.status == Node.Status.UNSEEN) {
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

    public Node getNewNode() {
        synchronized(nodesToSearch){
            while(nodesToSearch.isEmpty() && (!shutdown)) {
                try { 
                    nodesToSearch.wait(); 
                } catch(InterruptedException ignored) {}
            }

            if(nodesToSearch.isEmpty() && shutdown)
                return null;  
    
            return (Node) nodesToSearch.removeFirst();
        }
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
            controlStacks.get(searchId).push(node);
            tarjanStacks.get(searchId).push(node);
        }

        public void run() {
            Stack<Node> controlStack = controlStacks.get(searchId);
            Stack<Node> tarjanStack = tarjanStacks.get(searchId);

            while(true) {
                Node startNode = getNewNode();

                if(startNode == null)
                    return;

                addNode(startNode);

                while(!controlStack.isEmpty()) {
                    Node node = controlStack.lastElement();

                    synchronized(node) {
                        Set<Integer> outNeighbours = adjList.getOutEdges(node.id);

                        System.out.println("* " + node.id + " : " + this.searchId);
                        
                        if(outNeighbours != null && !outNeighbours.isEmpty()) {
                            int childId = outNeighbours.iterator().next();
                            adjList.removeOutEdge(node.id, childId);
                            
                            Node child = nodes.get(childId);

                            System.out.println("& " + childId + " : " + this.searchId);
                            
                            synchronized(child) {
                                if(child.status == Node.Status.UNSEEN)
                                    addNode(child);
                                else if(tarjanStack.contains(child))
                                node.updateLowLink(child.index);
                                else if(child.status != Node.Status.COMPLETE) {
                                    try {
                                        if(Suspend(this, node, child)) {
                                            child.wait();

                                            nodes.get(this.waitingFor).wait();

                                            Unsuspend(this, node);
                                        }
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }

                            // Adicionando nós para serem utilizados em outras buscas
                            for(int newNodeId : outNeighbours) {
                                if(newNodeId != childId)
                                    execute(nodes.get(newNodeId));
                            }
                        } else {
                            controlStack.pop();
                            
                            System.out.println("- " + node.id + " : " + this.searchId);
                            if(!controlStack.isEmpty())
                                controlStack.lastElement().updateLowLink(node.lowlink);

                            if(node.lowlink == node.index) {
                                Set<Integer> SCC = new HashSet<Integer>();
                                
                                Node vertex;
                                
                                do {
                                    vertex = tarjanStack.pop();

                                    synchronized(vertex) {
                                        SCC.add(vertex.id);
                                        vertex.status = Node.Status.COMPLETE;
                                        vertex.notifyAll();
                                    }
        
                                } while(vertex.id != node.id);
        
                                SCCs.add(SCC);
                            }
                        }
                    }
                }
            }
        }
    }
}
