import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

public class Search extends Thread {
    public int id;
    public int index;
    public Node waitingFor;
    public Stack<Node> controlStack;
    public Stack<Node> tarjanStack;
    private ArrayList<Set<Integer>> SCCs;

    private Suspended suspended;
    private Scheduler scheduler;

    public Search(int id, Suspended suspended, Scheduler scheduler) {
        this.id = id;
        
        this.index = 0;
        this.controlStack = new Stack<Node>();
        this.tarjanStack = new Stack<Node>();
        this.SCCs = new ArrayList<>();

        this.suspended = suspended;
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

    public Node getTransferNodes(Node blockerNode, ArrayList<Node> tempTarjan, ArrayList<Node> tempControl) {  
        Node node = tarjanStack.pop();
        int lowestLowlink = node.lowlink;
        boolean reachedBlocker = node == blockerNode;
        
        tempTarjan.add(node);

        while(!reachedBlocker || lowestLowlink < node.index) {
            node = tarjanStack.pop();
            tempTarjan.add(0, node);
            lowestLowlink = Math.min(lowestLowlink, node.lowlink);

            if(node == blockerNode)
                reachedBlocker = true;
        }

        Node oldestNode = node;

        node = controlStack.pop();
        tempControl.add(node);

        while(node.id != oldestNode.id) {
            node = controlStack.pop();
            tempControl.add(0, node);
        }

        Node oldWaitingFor = this.waitingFor;

        oldWaitingFor.blocked.remove(this);
        this.waitingFor = null;
        
        if(!this.tarjanStack.empty()) {   
            oldestNode.blocked.add(this);
            this.waitingFor = oldestNode;
        }

        return oldWaitingFor;
    }

    public ArrayList<Search> transferNodes(ArrayList<Node> senderTarjan, ArrayList<Node> senderControl) {
        int deltaIndex = this.index - controlStack.get(0).index;
        ArrayList<Search> allBlocked = new ArrayList<>();

        for(Node node : senderTarjan) {
            int newNodeIndex = node.tranfer(deltaIndex, this);

            for(Search blockedSearch : node.blocked) {
                if(!allBlocked.contains(blockedSearch))
                    allBlocked.add(0, blockedSearch);
            }

            this.index = Math.max(this.index, newNodeIndex);
            tarjanStack.push(node);
        }

        for(Node node : senderControl)
            controlStack.push(node);

        this.index += 1;

        return allBlocked;
    }

    private boolean expandEdge(Node parent, Node child) {     
        //System.out.println("& " + parent.id + " -> " + child.id +  " : s" + this.id + "\n");

        boolean wasSuspended = false;

        synchronized(child) {
            if(child.status == Node.Status.UNSEEN) {
                addNode(child);
                
            } else if(tarjanStack.contains(child)) {
                parent.updateLowLink(child.index);
                
            } else if(child.status != Node.Status.COMPLETE) {
                this.waitingFor = child;
                child.blocked.add(this);

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
            HashSet<Search> searchesToUnblock = new HashSet<>();

            do {
                vertex = tarjanStack.pop();
                //System.out.println("+ " + vertex.id + " : s" + this.id);

                synchronized(vertex) {
                    SCC.add(vertex.id);
                    vertex.status = Node.Status.COMPLETE;

                    if(vertex.blocked.size() > 0) {
                        ArrayList<Search> blockedSearches = new ArrayList<>(vertex.blocked);

                        for(Search blockedSearch : blockedSearches) {
                            vertex.blocked.remove(blockedSearch);
                            searchesToUnblock.add(blockedSearch);
                        }
                    }
                }
            } while(vertex.id != currentNode.id);

            for(Search blockedSearch : searchesToUnblock) {
                synchronized(blockedSearch) {
                    if(blockedSearch.waitingFor != null) {
                        //System.out.println("Unblock: s" + blockedSearch.id);
                        suspended.unsuspend(blockedSearch);
                        blockedSearch.waitingFor = null;
                        blockedSearch.notifyAll();
                    }
                }
            }

            //System.out.println("Finish SCC");

            SCCs.add(SCC);
        }
    }

    public void run() {
        while(!scheduler.shutdown) {
            //System.out.println("START SEARCH s" + id);
            
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
                Node child = scheduler.getUnexploredChild(node);
                
                //System.out.println("* " + node.id + " : s" + this.id);
                
                if(child != null) {
                    boolean wasSuspended = expandEdge(node, child);

                    if(!wasSuspended) {
                        //System.out.println("r " + node.id + " -> " + child.id +  " : s" + this.id + "\n");
                        scheduler.removeEdge(node, child);
                        // Adicionando nós para serem utilizados em outras buscas
                        scheduler.queueChildren(node);

                    } else if(wasSuspended && this.waitingFor != null) {
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