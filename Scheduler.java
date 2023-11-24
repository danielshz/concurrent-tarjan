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
    private HashMap<Integer, Integer> suspended;

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
        ArrayList<Integer> blokingCycle = blokingCycle(search.id, child.search);
        
        System.out.print("Ciclo: ");
        System.out.println(blokingCycle);
        
        if(blokingCycle == null) {
            suspended.put(search.id, child.search);
            System.out.println("Sem ciclo ::: s" + search.id);
            return true;
        }
        
        System.out.println("Com ciclo ::: s" + search.id);
        transferNodes(search, blokingCycle, child);
        // child.notifyAll();
          
        return false;
    }

    public synchronized void unsuspend(int id) {
        suspended.remove(id);
        searches[id].waitingFor = null;
    }

    private synchronized ArrayList<Integer> blokingCycle(int start, int target) {
        int current = target;

        System.out.println(":::::::::::::: w s" + target + " : b s" + start);
        
        ArrayList<Integer> path = new ArrayList<>();
        path.add(current);

        while(suspended.containsKey(current) && current != start) {
            current = suspended.get(current);

            if(current != start)
                path.add(current);
        }

        return current != start ? null : new ArrayList<Integer>(path.reversed());
    }

    private synchronized Node getTransferNodes(Node senderWaitingIn, Stack<Node> senderTarjanStack, Stack<Node> senderControlStack, ArrayList<Node> tempTarjan, ArrayList<Node> tempControl) {
        Node node = senderTarjanStack.pop();
        int lowestLowlink = node.lowlink;

        tempTarjan.add(node);

        while(node.id != senderWaitingIn.id || lowestLowlink < node.index) {
            node = senderTarjanStack.pop();
            tempTarjan.add(node);
            lowestLowlink = Math.min(lowestLowlink, node.lowlink);
        }

        Node oldestNode = node;

        node = senderControlStack.pop();
        tempControl.add(node);

        while(node.id != oldestNode.id) {
            node = senderControlStack.pop();
            tempControl.add(node);
        }

        return oldestNode;
    }

    private synchronized void transferNodes(Search receiverSearch, ArrayList<Integer> blokingPath, Node child) {
        Stack<Node> tarjanStack = receiverSearch.tarjanStack;
        Stack<Node> controlStack = receiverSearch.controlStack;

        ArrayList<ArrayList<Integer>> blockeds = new ArrayList<>();
        ArrayList<Integer> blockers = new ArrayList<>();

        HashMap<Integer, Node> waitingNodes = new HashMap<>();

        ArrayList<Integer> emptySearches = new ArrayList<>();
        ArrayList<Integer> nonEmptySearches = new ArrayList<>();

        Node n1 = child;

        for(int senderId : blokingPath) {
            Search senderSearch = searches[senderId];
            Stack<Node> senderTarjanStack = tarjanStacks.get(senderId);
            Stack<Node> senderControlStack = controlStacks.get(senderId);
            ArrayList<Node> tempTarjan = new ArrayList<>();
            ArrayList<Node> tempControl = new ArrayList<>();

            // ****** REVER ******
            Node newWaitingFor = getTransferNodes(n1, senderTarjanStack, senderControlStack, tempTarjan, tempControl);

            // Atualizando atributos
            Node oldWaitingFor = senderSearch.waitingFor;
            oldWaitingFor.blocked.remove((Integer) senderId);

            waitingNodes.put(senderId, oldWaitingFor);

            senderSearch.waitingFor = newWaitingFor;
            newWaitingFor.blocked.add(senderId);

            // suspended.put(senderId, newWaitingFor);

            n1 = oldWaitingFor;

            // Transferindo nós
            int deltaIndex = receiverSearch.index - newWaitingFor.index;

            ArrayList<Integer> allBlocked = new ArrayList<>();

            for(Node node : tempTarjan) {
                node.index += deltaIndex;
                node.lowlink += deltaIndex;
                node.search = receiverSearch.id;

                for(int blockedSearch : node.blocked) {
                    if(!allBlocked.contains(blockedSearch))
                        allBlocked.add(0, blockedSearch);
                }

                receiverSearch.index = Math.max(receiverSearch.index, node.index);
                tarjanStack.push(node);
            }

            for(Node node : tempControl)
                controlStack.push(node);

            receiverSearch.index += 1;

            System.out.println("\n\n\n\n\nControl: ");
            System.out.println(receiverSearch.controlStack.lastElement().id);

            System.out.println("\n\n\n\n\nTarjan: ");
            System.out.println(receiverSearch.tarjanStack.lastElement().id);

            // Pegar as buscas que estão bloqueadas por nós que foram transferidos
            allBlocked.removeIf(search -> search != receiverSearch.id && !blokingPath.contains(search));

            if(!allBlocked.isEmpty()) {
                blockeds.add(allBlocked);
                blockers.add(senderId);
            }

            if(senderTarjanStack.empty())
                emptySearches.add(senderId);
            else
                nonEmptySearches.add(senderId);

            // synchronized(oldWaitingFor) {
            //     oldWaitingFor.notify();
            // }
        }

        // Atualizando o suspended
        for(int i = 0; i < blockeds.size(); i++) {
            ArrayList<Integer> blockedSearches = blockeds.get(i);

            while(!blockedSearches.isEmpty()) {
                int blockedHead = blockedSearches.get(0);
                blockedSearches.remove(0);

                int blocker = blockers.get(i);

                if(suspended.containsKey(blockedHead)) {
                    if(suspended.get(blockedHead) == blocker)
                        suspended.put(blockedHead, blocker);
                }
            }
        }

        for(int emptySearch : emptySearches) {
            unsuspend(emptySearch);

            Node waitingNode = waitingNodes.get(emptySearch);
            synchronized(waitingNode) {
                waitingNode.notify();
            }

            suspended.remove(emptySearch);
        }
    
        // Coloquei a busca por enquanto, deveria ser o nó
        for(int nonEmptySearch : nonEmptySearches) {
            Node waitingNode = waitingNodes.get(nonEmptySearch);
            synchronized(waitingNode) {
                waitingNode.notify();
            }

            suspended.put(nonEmptySearch, receiverSearch.id);
        }
    }

    public void execute(Node node) {
        synchronized(nodesToSearch) {
            if(node != null && node.status == Node.Status.UNSEEN) {
                this.nodesToSearch.addLast(node);
                this.nodesToSearch.notify();
            }
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
    }

    public Node getNewNode() {
        synchronized(nodesToSearch) {
            if(nodesToSearch.isEmpty()) {
                Node startNode = Node.getNotInSCC(nodes);

                if(startNode == null)
                    this.shutdown = true;

                return startNode;
            }

            return (Node) nodesToSearch.removeFirst();
        }
    }
    private class Search extends Thread {
        public int id;
        public int index = 0;
        public Node waitingFor;
        public Stack<Node> controlStack;
        public Stack<Node> tarjanStack;

        public Search(int id) {
            this.id = id;
        }

        private void addNode(Node node) {
            node.index = this.index;
            node.lowlink = this.index;
            node.status = Node.Status.INPROGRESS;
            node.search = this.id;

            this.index++;
            controlStacks.get(id).push(node);
            tarjanStacks.get(id).push(node);
        }

        private boolean expandEdge(Node parent, Node child) {     
            System.out.println("& " + parent.id + " -> " + child.id +  " : s" + this.id + "\n");

            synchronized(child) {
                if(child.status == Node.Status.UNSEEN) {
                    addNode(child);
                    
                } else if(tarjanStack.contains(child)) {
                    parent.updateLowLink(child.index);
                    
                } else if(child.status != Node.Status.COMPLETE) {
                    try {
                        this.waitingFor = child;
                        child.blocked.add(this.id);
                        
                        if(Suspend(this, parent, child)) {
                            System.out.println("Before suspend");
                            child.wait();
                            System.out.println("Free suspend");
                            
                            return true; 
                        }
                        
                        this.waitingFor = null;
                        child.blocked.remove(this.id);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            
            return false;
        }

        private void backtrack(Node currentNode) {
            controlStack.pop();
            
            System.out.println("- " + currentNode.id + " : s" + this.id);

            if(!controlStack.isEmpty())
                controlStack.lastElement().updateLowLink(currentNode.lowlink);
            
            if(currentNode.lowlink == currentNode.index) {
                Set<Integer> SCC = new HashSet<Integer>();
                
                Node vertex;

                if(tarjanStack.isEmpty())
                    return;
                
                do {
                    vertex = tarjanStack.pop();
                    System.out.println("+ " + vertex.id + " : s" + this.id);
                    
                    synchronized(vertex) {
                        SCC.add(vertex.id);
                        vertex.status = Node.Status.COMPLETE;

                        if(vertex.blocked.size() > 0) {
                            System.out.println(vertex.blocked);

                            java.util.Iterator<Integer> blockedIterator = vertex.blocked.iterator();

                            while(blockedIterator.hasNext()) {
                                int blockedSearch = blockedIterator.next();
                                unsuspend(blockedSearch);
                                blockedIterator.remove();
                            }
                        
                            // for(int blockedSearch : vertex.blocked) {
                            //     if(blockedSearch != this.id) {
                            //         vertex.blocked.remove((Integer) blockedSearch);
                            //         unsuspend(blockedSearch);
                            //     }
                            // }

                            vertex.notifyAll();
                        }
                    }
                } while(vertex.id != currentNode.id);

                SCCs.add(SCC);
            }
        }

        public void run() {
            this.controlStack = controlStacks.get(id);
            this.tarjanStack = tarjanStacks.get(id);

            while(!shutdown) {
                System.out.println("-------------------- s" + id + "---------------");
                
                // Resetando atributos
                tarjanStack.clear();
                controlStack.clear();
                this.index = 0;

                Node startNode = getNewNode();

                if(startNode == null || startNode.status != Node.Status.UNSEEN) {
                    System.out.println("Nó já visitado");
                    continue;
                }

                addNode(startNode);

                while(!controlStack.isEmpty()) {
                    Node node = controlStack.lastElement();
                    
                    Set<Integer> outNeighbours = adjList.getOutEdges(node.id);
                    
                    System.out.println("* " + node.id + " : s" + this.id);
                    
                    if(outNeighbours != null && !outNeighbours.isEmpty()) {
                        int childId = outNeighbours.iterator().next();
                        Node child = nodes.get(childId);

                        boolean wasSuspended = expandEdge(node, child);

                        if(!wasSuspended) {
                            // adjList.removeOutEdge(node.id, child.index);
                            outNeighbours.remove(child.id);
                            // outNeighbours = adjList.getOutEdges(node.id);

                            // Adicionando nós para serem utilizados em outras buscas
                            for(int newNodeId : outNeighbours) {
                                if(newNodeId != childId) {
                                    System.out.println("= " + node.id + " -> " + newNodeId + " : s" + this.id);
                                    execute(nodes.get(newNodeId));
                                }
                            }

                            System.out.println("");
                        } else if(this.waitingFor != null) {
                            Node newSuspendedNode = this.waitingFor;

                            synchronized(newSuspendedNode) {
                                if(this.waitingFor.id == newSuspendedNode.id) {
                                    try {
                                        System.out.println("Suspended " + newSuspendedNode.id + " s" + this.id);
                                        newSuspendedNode.wait();
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        } 
                    } else {
                        backtrack(node);   
                    }
                }
            }
        }
    }
}
