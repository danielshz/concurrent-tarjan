import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

public class Search extends Thread {
    public int id;
    public int index;
    public Node waitingFor;
    public Stack<Node> controlStack;
    public Stack<Node> tarjanStack;
    private ArrayList<Set<Integer>> SCCs;

    private Suspended suspended;
    private AdjacencyList adjList;
    private ConcurrentHashMap<Integer, Node> nodes;
    private Scheduler scheduler;

    public Search(int id, Suspended suspended, AdjacencyList adjList, Scheduler scheduler, ConcurrentHashMap<Integer, Node> nodes) {
        this.id = id;
        
        this.index = 0;
        this.controlStack = new Stack<Node>();
        this.tarjanStack = new Stack<Node>();
        this.SCCs = new ArrayList<>();

        this.suspended = suspended;
        this.adjList = adjList;
        this.nodes = nodes;
        this.scheduler = scheduler;
    }

    public ArrayList<Set<Integer>> getSCCs() {
        return this.SCCs;
    }

    private void addNode(Node node) {
        node.index = this.index;
        node.lowlink = this.index;
        node.status = Node.Status.INPROGRESS;
        node.search = this;

        this.index++;
        controlStack.push(node);
        tarjanStack.push(node);
    }

    private boolean expandEdge(Node parent, Node child) {     
        //System.out.println("& " + parent.id + " -> " + child.id +  " : s" + this.id + "\n");

        boolean wasSuspended = false;
        boolean inProgress = false;

        synchronized(child) {
            if(child.status == Node.Status.UNSEEN) {
                addNode(child);
                
            } else if(tarjanStack.contains(child)) {
                if(child.search != parent.search)
                    throw new RuntimeException("Diferente busca");
                parent.updateLowLink(child.index);
                
            } else if(child.status != Node.Status.COMPLETE) {
                this.waitingFor = child;
                child.blocked.add(this);

                inProgress = true;
                
                if(inProgress)
                    wasSuspended = suspended.suspend(this, child);

                if(!wasSuspended) {
                    child.blocked.remove(this);
                    this.waitingFor = null;
                }
            }
        }
        
        if(wasSuspended) {
            try {
                synchronized(this) {
                    if(this.waitingFor == child) {
                        //System.out.println("Suspend s" + this.id);
                        this.wait();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            //System.out.println("Free s" + this.id);
        }

        return wasSuspended;
    }

    private void backtrack(Node currentNode) {
        controlStack.pop();
        
        //System.out.println("- " + currentNode.id + " : s" + this.id);

        if(!controlStack.isEmpty())
            controlStack.peek().updateLowLink(currentNode.lowlink);
        
        if(currentNode.lowlink == currentNode.index) {
            Set<Integer> SCC = new HashSet<Integer>();
            
            Node vertex;

            if(tarjanStack.isEmpty())
                return;
            
            HashSet<Search> unblockedSearches = new HashSet<>();

            do {
                vertex = tarjanStack.pop();
                //System.out.println("+ " + vertex.id + " : s" + this.id);

                synchronized(vertex) {
                    SCC.add(vertex.id);
                    vertex.status = Node.Status.COMPLETE;

                    if(vertex.blocked.size() > 0) {
                        // String blockedString = "";

                        // for(Search search2 : vertex.blocked) {
                        //     blockedString += "s" + search2.id + " ";    
                        // }
                
                        // //System.out.println("Unblock: " + blockedString);

                        ArrayList<Search> blockedVertex = new ArrayList<>(vertex.blocked);

                        for(Search blockedSearch : blockedVertex) {
                            // suspended.unsuspend(blockedSearch);
                            // blockedSearch.waitingFor = null;
                            vertex.blocked.remove(blockedSearch);
                            unblockedSearches.add(blockedSearch);
                        }

                        // vertex.notifyAll();
                    }
                }
            } while(vertex.id != currentNode.id);

            for(Search search : unblockedSearches) {
                synchronized(search) {
                    //System.out.println("Unblock: s" + search.id);
                    suspended.unsuspend(search);
                    search.notifyAll();
                }
            }

            //System.out.println("SCC");

            SCCs.add(SCC);
        }
    }

    public void run() {
        while(!scheduler.shutdown) {
            //System.out.println("-------------------- s" + id + "---------------");
            
            // Resetando atributos
            tarjanStack.clear();
            controlStack.clear();
            this.index = 0;

            Node startNode = scheduler.getNewNode();

            if(startNode != null) {
                synchronized(startNode) {
                    if(startNode.status != Node.Status.UNSEEN)
                        continue;
        
                    addNode(startNode);
                }
            }

            while(!controlStack.isEmpty()) {
                System.out.println(".");

                Node node = controlStack.peek();
                
                Set<Integer> outNeighbours = adjList.getOutEdges(node.id);
                
                //System.out.println("* " + node.id + " : s" + this.id);
                
                if(outNeighbours != null && !outNeighbours.isEmpty()) {
                    int childId = outNeighbours.iterator().next();
                    Node child = nodes.get(childId);

                    boolean wasSuspended = expandEdge(node, child);

                    if(!wasSuspended) {
                        //System.out.println("r " + node.id + " -> " + child.id +  " : s" + this.id + "\n");
                        // adjList.removeOutEdge(node.id, child.index);
                        outNeighbours.remove(child.id);
                        // outNeighbours = adjList.getOutEdges(node.id);

                        // Adicionando nós para serem utilizados em outras buscas
                        for(int newNodeId : outNeighbours) {
                            if(newNodeId != childId) {
                                boolean added = scheduler.queueNewNode(nodes.get(newNodeId));

                                //if(added)
                                    //System.out.println("= " + node.id + " -> " + newNodeId + " : s" + this.id);
                            }
                        }

                        //System.out.println("");
                    } else if(this.waitingFor != null) {
                        synchronized(this) {
                            if(this.waitingFor != null) {
                                try {
                                    //System.out.println("New suspended " + this.waitingFor.id + " s" + this.id);
                                    this.wait();
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

        //System.out.println("FIM s" + this.id);
    }
}